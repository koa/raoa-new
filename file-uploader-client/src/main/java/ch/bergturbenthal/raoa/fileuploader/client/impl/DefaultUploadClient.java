package ch.bergturbenthal.raoa.fileuploader.client.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;

import ch.bergturbenthal.raoa.fileuploader.client.UploaderClient;
import ch.bergturbenthal.raoa.service.file.upload.FileUploadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.FileUploadServiceGrpc.FileUploadServiceFutureStub;
import ch.bergturbenthal.raoa.service.file.upload.FragmentRequest;
import ch.bergturbenthal.raoa.service.file.upload.FragmentResponse;
import ch.bergturbenthal.raoa.service.file.upload.FragmentResponse.ResponseState;
import io.grpc.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultUploadClient implements UploaderClient {

    private final Executor executor;

    public DefaultUploadClient(final Executor executor) {
        this.executor = executor;
    }

    @Override
    public ListenableFuture<Void> sendFile(final InputStream data, final long handle, final Channel channel) {
        final FileUploadServiceFutureStub stub = FileUploadServiceGrpc.newFutureStub(channel);
        final SettableFuture<Void> response = SettableFuture.create();
        sendFragment(data, stub, response, handle);
        return response;
    }

    private void sendFragment(final InputStream data, final FileUploadServiceFutureStub stub, final SettableFuture<Void> response,
            final long handle) {
        try {
            final byte[] buffer = new byte[1024 * 1024];
            int ptr = 0;
            while (true) {
                final int read = data.read(buffer, ptr, buffer.length - ptr);
                if (ptr < 0) {
                    break;
                }
                ptr = +read;
            }
            final ByteString value = ByteString.copyFrom(buffer, 0, ptr);
            if (value.isEmpty()) {
                // no more data
                response.set(null);
                return;
            }
            final FragmentRequest request = FragmentRequest.newBuilder().setData(value).setSendHandle(handle).build();
            final ListenableFuture<FragmentResponse> future = stub.sendFragment(request);
            future.addListener(() -> {
                try {
                    final FragmentResponse fragmentResponse = future.get();
                    final ResponseState state = fragmentResponse.getState();
                    if (state == ResponseState.OK) {
                        // send next fragment
                        sendFragment(data, stub, response, handle);
                    } else {
                        // TODO: handle errors
                        log.error("Error sending file to server " + state);
                        response.set(null);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Cannot take response on send", e);
                    response.set(null);
                }
            }, executor);
        } catch (final IOException e) {
            log.error("Cannot read data to send", e);
            response.set(null);
        }
    }

}
