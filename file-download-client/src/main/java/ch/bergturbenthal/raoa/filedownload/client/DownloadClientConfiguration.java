package ch.bergturbenthal.raoa.filedownload.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.bergturbenthal.raoa.filedownload.client.impl.DefaultDownloadClient;

@Configuration
public class DownloadClientConfiguration {
    @Bean
    public DownloadClient downloadClient() {
        return new DefaultDownloadClient();
    }

}
