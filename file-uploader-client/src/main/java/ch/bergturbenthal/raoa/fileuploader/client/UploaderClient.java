package ch.bergturbenthal.raoa.fileuploader.client;

import java.io.InputStream;

import io.grpc.Channel;
import reactor.core.publisher.Mono;

public interface UploaderClient {
    Mono<Void> sendFile(InputStream data, long handle, Channel channel);
}
