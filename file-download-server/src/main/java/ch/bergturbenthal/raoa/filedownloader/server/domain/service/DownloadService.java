package ch.bergturbenthal.raoa.filedownloader.server.domain.service;

import java.io.IOException;
import java.io.InputStream;

import ch.bergturbenthal.raoa.service.file.download.FileFragment;
import reactor.core.publisher.Flux;

public interface DownloadService {
    public interface DownloadDataSource {
        default void downloadFinished() {

        }

        InputStream getInputStream() throws IOException;
    }

    long anounceFile(DownloadDataSource dataSource);

    Flux<FileFragment> downloadFile(long handle);
}
