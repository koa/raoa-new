package ch.bergturbenthal.raoa.fileuploader.client;

import java.io.InputStream;

import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.Channel;

public interface UploaderClient {
    ListenableFuture<Void> sendFile(InputStream data, long handle, Channel channel);
}
