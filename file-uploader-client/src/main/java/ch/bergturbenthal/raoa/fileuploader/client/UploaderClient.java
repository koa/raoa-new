package ch.bergturbenthal.raoa.fileuploader.client;

import io.grpc.Channel;
import java.io.InputStream;
import reactor.core.publisher.Mono;

public interface UploaderClient {
  Mono<Void> sendFile(InputStream data, long handle, Channel channel);
}
