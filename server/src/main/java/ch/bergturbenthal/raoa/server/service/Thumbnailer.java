package ch.bergturbenthal.raoa.server.service;

import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

public interface Thumbnailer {
  Mono<Resource> createThumbnail(Resource originalData);
}
