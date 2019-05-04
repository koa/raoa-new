package ch.bergturbenthal.raoa.server.service.impl;

import ch.bergturbenthal.raoa.filedownload.client.DownloadClient;
import ch.bergturbenthal.raoa.fileuploader.client.UploaderClient;
import ch.bergturbenthal.raoa.server.configuration.RaoaProperties;
import ch.bergturbenthal.raoa.server.service.Thumbnailer;
import ch.bergturbenthal.raoa.service.thumbnailer.*;
import ch.bergturbenthal.raoa.service.thumbnailer.ThumbnailerServiceGrpc.ThumbnailerServiceStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Slf4j
@Service
public class LoadbalancingThumbnailClient implements Thumbnailer {

  private final RaoaProperties configuration;
  private final DiscoveryClient discoveryClient;
  private final Map<URI, ConnectionData> knownChannels = new ConcurrentHashMap<>();
  private final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
  private final UploaderClient fileUploadClient;
  private final DownloadClient downloadClient;
  private final Semaphore drainSemaphore = new Semaphore(1);

  public LoadbalancingThumbnailClient(
      final RaoaProperties configuration,
      final DiscoveryClient discoveryClient,
      final UploaderClient fileUploadClient,
      final DownloadClient downloadClient) {
    this.configuration = configuration;
    this.discoveryClient = discoveryClient;
    this.fileUploadClient = fileUploadClient;
    this.downloadClient = downloadClient;
  }

  @Override
  public Mono<Resource> createThumbnail(final Resource originalData) {

    return Mono.create(
        sink -> {
          queue.add(new QueueEntry(originalData, sink));
          drainQueue();
        });
  }

  private void drainQueue() {
    if (!drainSemaphore.tryAcquire()) {
      return;
    }
    try {
      while (true) {
        final QueueEntry queueEntry = queue.poll();
        if (queueEntry == null) {
          break;
        }
        if (!trySend(queueEntry)) {
          // send failed
          putBackInQueue(queueEntry);
          break;
        }
      }
    } finally {
      drainSemaphore.release();
    }
  }

  private void putBackInQueue(final QueueEntry queueEntry) {
    queue.add(queueEntry);
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
            ThumbnailerServiceGrpc.newStub(channel)
                .getCapabilities(
                    Empty.newBuilder().build(),
                    new StreamObserver<ThumbnailerCapabilities>() {

                      @Override
                      public void onCompleted() {
                        if (patternReference.get().isEmpty()) {
                          // unusable thumbnailer
                          knownChannels.remove(k);
                          channel.shutdown();
                        }
                      }

                      @Override
                      public void onError(final Throwable t) {
                        log.error("Cannot query capabilities of connection " + k, t);
                        patternReference.set(Collections.emptyList());
                        knownChannels.remove(k);
                      }

                      @Override
                      public void onNext(final ThumbnailerCapabilities value) {
                        patternReference.set(
                            value.getRegexList().stream()
                                .map(s -> Pattern.compile(s))
                                .collect(Collectors.toList()));
                      }
                    });
            // channel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, () -> {
            // patternReference.set(Collections.emptyList());
            // knownChannels.remove(k);
            // });
            return new ConnectionData(channel, patternReference);
          });
      remainingUris.remove(connectionId);
    }
    for (final URI uri : remainingUris) {
      try {
        final ConnectionData connectionData = knownChannels.remove(uri);
        if (connectionData != null) {
          final ManagedChannel channel = connectionData.getChannel();
          channel.shutdown();
        }
      } catch (final Exception e) {
        log.error("Cannot cleanup connection to " + uri);
      }
    }
    drainQueue();
  }

  private boolean trySend(final QueueEntry queueEntry) {
    final Resource sourceData = queueEntry.getSourceData();
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
    final MonoSink<Resource> resultSink = queueEntry.getResponseHolder();
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
        final ManagedChannel channel = connectionData.getChannel();
        final ThumbnailerServiceStub thumbnailerService = ThumbnailerServiceGrpc.newStub(channel);
        thumbnailerService.generateThumbnail(
            generationRequest,
            new StreamObserver<GenerationResponse>() {

              @Override
              public void onCompleted() {
                // TODO Auto-generated method stub

              }

              @Override
              public void onError(final Throwable t) {
                resultSink.error(t);
              }

              @Override
              public void onNext(final GenerationResponse response) {
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
                      fileUploadClient
                          .sendFile(sourceData.getInputStream(), fileUploadHandle, channel)
                          .subscribe(
                              value -> {},
                              ex -> resultSink.error(new RuntimeException("Cannot send file", ex)));
                      break;
                    case FILEREADYRESPONSE:
                      final FileGeneratedMessage readyResponse = response.getFileReadyResponse();
                      final long fileDownloadHandle = readyResponse.getFileDownloadHandle();
                      final long fileSize = readyResponse.getFileSize();
                      resultSink.success(
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
                                  .downloadFile(fileDownloadHandle, channel)
                                  .subscribe(
                                      block -> {
                                        try {
                                          bufferedSegments.put(Optional.of(block));
                                        } catch (final InterruptedException e) {
                                          error.set(e);
                                        }
                                      },
                                      ex -> {
                                        error.set(ex);
                                      },
                                      () -> {
                                        try {
                                          bufferedSegments.put(Optional.empty());
                                        } catch (final InterruptedException e) {
                                          error.set(e);
                                        }
                                        connectionData.getBlockUntil().set(null);
                                        drainQueue();
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
                                    throw new IOException("Waiting on next segment interrupted", e);
                                  }
                                }
                              };
                            }
                          });
                      break;
                    case OVERLOADEDRESPONSE:
                      log.info("Overloaded");
                      connectionData.getBlockUntil().set(Instant.now().plus(3, ChronoUnit.SECONDS));
                      putBackInQueue(queueEntry);
                      break;
                    default:
                      putBackInQueue(queueEntry);
                      break;
                  }
                } catch (final IOException e) {
                  resultSink.error(e);
                }
              }
            });
        return true;
      }
    } catch (final IOException ex) {
      resultSink.error(ex);
    }
    return false;
  }

  @Value
  private static class ConnectionData {
    private ManagedChannel channel;
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

  @Value
  private static class QueueEntry {
    private Resource sourceData;
    private MonoSink<Resource> responseHolder;
  }
}
