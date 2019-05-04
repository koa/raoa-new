package ch.bergturbenthal.raoa.fileuploader.client.impl;

import ch.bergturbenthal.raoa.fileuploader.client.UploaderClient;
import ch.bergturbenthal.raoa.service.file.upload.FileUploadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.FileUploadServiceGrpc.FileUploadServiceFutureStub;
import ch.bergturbenthal.raoa.service.file.upload.FragmentRequest;
import ch.bergturbenthal.raoa.service.file.upload.FragmentResponse;
import ch.bergturbenthal.raoa.service.file.upload.FragmentResponse.ResponseState;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import io.grpc.Channel;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Slf4j
public class DefaultUploadClient implements UploaderClient {

  private final Executor executor;

  public DefaultUploadClient(final Executor executor) {
    this.executor = executor;
  }

  @Override
  public Mono<Void> sendFile(final InputStream data, final long handle, final Channel channel) {
    return Mono.create(
        sink -> {
          final FileUploadServiceFutureStub stub = FileUploadServiceGrpc.newFutureStub(channel);
          sendFragment(data, stub, sink, handle, 0);
        });
  }

  private void sendFragment(
      final InputStream data,
      final FileUploadServiceFutureStub stub,
      final MonoSink<Void> sink,
      final long handle,
      final int offset) {
    try {
      final byte[] buffer = new byte[1024 * 1024];
      int ptr = 0;
      while (ptr < buffer.length) {
        final int read = data.read(buffer, ptr, buffer.length - ptr);
        if (read < 0) {
          break;
        }
        ptr += read;
      }
      final ByteString value = ByteString.copyFrom(buffer, 0, ptr);
      final int size = value.size();
      if (value.isEmpty()) {
        // no more data
        log.info("Upload finished");
        sink.success();
        return;
      }
      final FragmentRequest request =
          FragmentRequest.newBuilder().setData(value).setSendHandle(handle).build();
      final ListenableFuture<FragmentResponse> future = stub.sendFragment(request);
      future.addListener(
          () -> {
            try {
              final FragmentResponse fragmentResponse = future.get();
              final ResponseState state = fragmentResponse.getState();
              if (state == ResponseState.OK) {
                // send next fragment
                sendFragment(data, stub, sink, handle, offset + size);
              } else {
                // TODO: handle errors
                sink.error(new RuntimeException("Error sending file to server " + state));
              }
            } catch (InterruptedException | ExecutionException e) {
              sink.error(new RuntimeException("Cannot take response on send", e));
            }
          },
          executor);
    } catch (final IOException e) {
      sink.error(new RuntimeException("Cannot read data to send", e));
    }
  }
}
