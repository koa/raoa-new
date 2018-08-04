package ch.bergturbenthal.raoa.filedownloader.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService;
import ch.bergturbenthal.raoa.filedownloader.server.domain.service.impl.DefaultDownloadService;
import ch.bergturbenthal.raoa.filedownloader.server.interfaces.FileDownloadService;

@Configuration
public class FileDownloadServiceConfiguration {
    @Bean
    public DownloadService downloadService() {
        return new DefaultDownloadService();
    }

    @Bean
    public FileDownloadService fileDownloadService(final DownloadService downloadService) {
        return new FileDownloadService(downloadService);
    }
}
