package ch.bergturbenthal.raoa.server.service.impl;

import ch.bergturbenthal.raoa.filedownload.client.DownloadClient;
import ch.bergturbenthal.raoa.fileuploader.client.UploaderClient;
import ch.bergturbenthal.raoa.server.configuration.RaoaProperties;
import ch.bergturbenthal.raoa.server.service.Thumbnailer;
import ch.bergturbenthal.raoa.service.thumbnailer.FileGeneratedMessage;
import ch.bergturbenthal.raoa.service.thumbnailer.GenerationRequest;
import ch.bergturbenthal.raoa.service.thumbnailer.ReactorThumbnailerServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Service
public class LoadbalancingThumbnailClient implements Thumbnailer {

  private final RaoaProperties configuration;
  private final DiscoveryClient discoveryClient;
  private final Map<URI, ConnectionData> knownChannels = new ConcurrentHashMap<>();
  private final UploaderClient fileUploadClient;
  private final DownloadClient downloadClient;
  private final FlushScheduler scheduler = new FlushScheduler();

  public LoadbalancingThumbnailClient(
      final RaoaProperties configuration,
      final DiscoveryClient discoveryClient,
      final UploaderClient fileUploadClient,
      final DownloadClient downloadClient) {
    this.configuration = configuration;
    this.discoveryClient = discoveryClient;
    this.fileUploadClient = fileUploadClient;
    this.downloadClient = downloadClient;
    refreshKnownClientsEndpoints();
  }

  @Override
  public Mono<Resource> createThumbnail(final Resource originalData) {
    while (scheduler.currentQueueSize() > 50) {
      scheduler.flush();
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        log.warn("Cancelled", e);
      }
    }
    scheduler.flush();
    return doSend(originalData).subscribeOn(scheduler);
  }

  @Scheduled(fixedDelay = 60 * 1000)
  public void refreshKnownClientsEndpoints() {
    final String thumbnailerService = configuration.getThumbnailerService();
    final List<ServiceInstance> thumbnailerInstances =
        discoveryClient.getInstances(thumbnailerService);
    final Collection<URI> remainingUris = new HashSet<>(knownChannels.keySet());
    for (final ServiceInstance serviceInstance : thumbnailerInstances) {
      final URI connectionId = serviceInstance.getUri();
      knownChannels.computeIfAbsent(
          connectionId,
          k -> {
            final ManagedChannelBuilder<?> channelBuilder =
                ManagedChannelBuilder.forAddress(
                    serviceInstance.getHost(), serviceInstance.getPort());
            if (serviceInstance.isSecure()) {
              channelBuilder.useTransportSecurity();
            } else {
              channelBuilder.usePlaintext();
            }
            final ManagedChannel channel = channelBuilder.build();
            final AtomicReference<Collection<Pattern>> patternReference =
                new AtomicReference<>(Collections.emptyList());
            final ReactorThumbnailerServiceGrpc.ReactorThumbnailerServiceStub serviceStub =
                ReactorThumbnailerServiceGrpc.newReactorStub(channel);
            serviceStub
                .getCapabilities(Empty.newBuilder().build())
                // .publishOn(scheduler)
                .subscribe(
                    value -> {
                      patternReference.set(
                          value.getRegexList().stream()
                              .map(Pattern::compile)
                              .collect(Collectors.toList()));
                    },
                    ex -> {
                      log.error("Cannot query capabilities of connection " + k, ex);
                      patternReference.set(Collections.emptyList());
                      knownChannels.remove(k);
                    },
                    () -> {
                      if (patternReference.get().isEmpty()) {
                        // unusable thumbnailer
                        knownChannels.remove(k);
                        channel.shutdown();
                      }
                    });
            return new ConnectionData(serviceStub, patternReference);
          });
      remainingUris.remove(connectionId);
    }
    for (final URI uri : remainingUris) {
      try {
        final ConnectionData connectionData = knownChannels.remove(uri);
        if (connectionData != null) {
          final Channel channel = connectionData.getServiceStub().getChannel();
          if (channel instanceof ManagedChannel) ((ManagedChannel) channel).shutdown();
        }
      } catch (final Exception e) {
        log.error("Cannot cleanup connection to " + uri);
      }
    }
  }

  private Mono<Resource> doSend(Resource sourceData) {
    final String filename = sourceData.getFilename();
    final List<URI> matchingChannels = new ArrayList<>();
    for (final Entry<URI, ConnectionData> uri : knownChannels.entrySet()) {
      final ConnectionData channelData = uri.getValue();
      final Instant blockUntil = channelData.getBlockUntil().get();
      if (blockUntil != null && blockUntil.isAfter(Instant.now())) {
        continue;
      }
      final boolean anyMatch =
          channelData.getFilenamePattern().get().stream()
              .anyMatch(p -> p.matcher(filename).matches());
      if (anyMatch) {
        matchingChannels.add(uri.getKey());
      }
    }
    Collections.shuffle(matchingChannels);
    final Iterator<URI> channelIterator = matchingChannels.iterator();
    try {
      final GenerationRequest generationRequest =
          GenerationRequest.newBuilder()
              .setFilename(filename)
              .setLength(sourceData.contentLength())
              .build();
      while (channelIterator.hasNext()) {
        final URI id = channelIterator.next();
        final ConnectionData connectionData = knownChannels.get(id);
        if (connectionData == null) {
          continue;
        }
        final ReactorThumbnailerServiceGrpc.ReactorThumbnailerServiceStub thumbnailerService =
            connectionData.getServiceStub();
        return thumbnailerService
            .generateThumbnail(generationRequest)
            .log("generate-response")
            .flatMap(
                response -> {
                  try {
                    switch (response.getResponseCase()) {
                      case FILEUPLOADHANDLE:
                        final Instant blockUntil = connectionData.getBlockUntil().get();
                        if (blockUntil != null && blockUntil.isBefore(Instant.now())) {
                          // remove lock
                          log.info("Lock removed");
                          connectionData.getRepeatCount().set(0);
                          connectionData.getBlockUntil().set(null);
                        }
                        final long fileUploadHandle = response.getFileUploadHandle();
                        return fileUploadClient
                            .sendFile(
                                sourceData.getInputStream(),
                                fileUploadHandle,
                                thumbnailerService.getChannel())
                            .log("Upload")
                            .flatMap(v -> Mono.empty());
                      case FILEREADYRESPONSE:
                        final FileGeneratedMessage readyResponse = response.getFileReadyResponse();
                        final long fileDownloadHandle = readyResponse.getFileDownloadHandle();
                        final long fileSize = readyResponse.getFileSize();
                        return Mono.just(
                            new AbstractResource() {

                              @Override
                              public long contentLength() throws IOException {
                                return fileSize;
                              }

                              @Override
                              public boolean exists() {
                                return true;
                              }

                              @Override
                              public String getDescription() {
                                return "Download Resource " + fileDownloadHandle;
                              }

                              @Override
                              public InputStream getInputStream() throws IOException {
                                final BlockingQueue<Optional<ByteString>> bufferedSegments =
                                    new LinkedBlockingQueue<>(2);
                                final AtomicReference<Throwable> error =
                                    new AtomicReference<Throwable>(null);
                                downloadClient
                                    .downloadFile(
                                        fileDownloadHandle, thumbnailerService.getChannel())
                                    .subscribe(
                                        block -> {
                                          try {
                                            bufferedSegments.put(Optional.of(block));
                                          } catch (final InterruptedException e) {
                                            error.set(e);
                                          }
                                        },
                                        error::set,
                                        () -> {
                                          try {
                                            bufferedSegments.put(Optional.empty());
                                          } catch (final InterruptedException e) {
                                            error.set(e);
                                          }
                                          connectionData.getBlockUntil().set(null);
                                        });
                                return new InputStream() {
                                  private CurrentReadingEntry currentEntry = null;

                                  @Override
                                  public int read() throws IOException {
                                    final byte[] buffer = new byte[1];
                                    final int count = read(buffer);
                                    if (count < 1) {
                                      return -1;
                                    }
                                    return buffer[0];
                                  }

                                  @Override
                                  public synchronized int read(
                                      final byte[] b, final int off, final int len)
                                      throws IOException {
                                    try {
                                      int count = 0;
                                      while (count < len) {
                                        if (currentEntry == null || currentEntry.empty()) {
                                          final Optional<ByteString> nextSegment =
                                              bufferedSegments.take();
                                          if (!nextSegment.isPresent()) {
                                            bufferedSegments.put(Optional.empty());
                                            if (count == 0) {
                                              return -1;
                                            }
                                            break;
                                          }
                                          currentEntry = new CurrentReadingEntry(nextSegment.get());
                                        }
                                        final Throwable foundError = error.get();
                                        if (foundError != null) {
                                          throw new IOException("Error from server", foundError);
                                        }
                                        final int read =
                                            currentEntry.read(b, off + count, len - count);
                                        count += read;
                                      }
                                      return count;
                                    } catch (final InterruptedException e) {
                                      throw new IOException(
                                          "Waiting on next segment interrupted", e);
                                    }
                                  }
                                };
                              }
                            });
                      case OVERLOADEDRESPONSE:
                        log.info("Overloaded");
                        connectionData
                            .getBlockUntil()
                            .set(Instant.now().plus(3, ChronoUnit.SECONDS));
                        return doSend(sourceData);
                      default:
                        return doSend(sourceData);
                    }
                  } catch (final IOException e) {
                    return Mono.error(e);
                  }
                })
            .next();
      }
    } catch (final IOException ex) {
      return Mono.error(ex);
    }
    return Mono.just(Boolean.TRUE).publishOn(scheduler).flatMap(l -> doSend(sourceData));
  }

  @Value
  private static class ConnectionData {
    private ReactorThumbnailerServiceGrpc.ReactorThumbnailerServiceStub serviceStub;
    private AtomicReference<Collection<Pattern>> filenamePattern;
    private AtomicReference<Instant> blockUntil = new AtomicReference<Instant>(null);
    private AtomicInteger repeatCount = new AtomicInteger(0);
  }

  public static class CurrentReadingEntry {
    private final ByteString dataSegment;
    private int ptr = 0;

    public CurrentReadingEntry(final ByteString dataSegment) {
      this.dataSegment = dataSegment;
    }

    public boolean empty() {
      return ptr >= dataSegment.size();
    }

    public int read(final byte[] target, final int start, final int count) {
      final int maxRead = dataSegment.size() - ptr;
      final int countToRead = Math.min(maxRead, count);
      dataSegment.copyTo(target, ptr, start, countToRead);
      ptr += countToRead;
      return countToRead;
    }
  }

  private static class FlushScheduler implements Scheduler {
    private BlockingQueue<Runnable> pendingRunnables = new ArrayBlockingQueue<>(100);

    public void flush() {
      final List<Runnable> runnables = new ArrayList<>();
      pendingRunnables.drainTo(runnables);
      runnables.forEach(Runnable::run);
    }

    public int currentQueueSize() {
      return pendingRunnables.size();
    }

    @Override
    public Disposable schedule(final Runnable task) {
      pendingRunnables.add(task);
      return () -> {
        pendingRunnables.remove(task);
      };
    }

    @Override
    public Worker createWorker() {
      final FlushScheduler flushScheduler = FlushScheduler.this;
      return new Worker() {
        @Override
        public Disposable schedule(final Runnable task) {
          return flushScheduler.schedule(task);
        }

        @Override
        public void dispose() {}
      };
    }
  }
}
