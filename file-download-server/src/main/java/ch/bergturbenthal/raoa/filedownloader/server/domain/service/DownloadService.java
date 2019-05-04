package ch.bergturbenthal.raoa.filedownloader.server.domain.service;

import ch.bergturbenthal.raoa.service.file.download.FileFragment;
import java.io.IOException;
import java.io.InputStream;
import reactor.core.publisher.Flux;

public interface DownloadService {
  long anounceFile(DownloadDataSource dataSource);

  Flux<FileFragment> downloadFile(long handle);

  public interface DownloadDataSource {
    default void downloadFinished() {}

    InputStream getInputStream() throws IOException;
  }
}
