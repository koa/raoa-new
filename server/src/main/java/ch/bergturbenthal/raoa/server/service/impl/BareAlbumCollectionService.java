package ch.bergturbenthal.raoa.server.service.impl;

import ch.bergturbenthal.raoa.server.configuration.RaoaProperties;
import ch.bergturbenthal.raoa.server.service.AlbumService;
import java.io.File;
import org.springframework.stereotype.Service;

@Service
public class BareAlbumCollectionService {
  public BareAlbumCollectionService(
      final AlbumService albumService, final RaoaProperties properties) {
    for (final File file : properties.getStorageBase().listFiles()) {
      albumService.detectAlbum(file);
    }
  }
}
