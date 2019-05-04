package ch.bergturbenthal.raoa.filedownload.client;

import ch.bergturbenthal.raoa.filedownload.client.impl.DefaultDownloadClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DownloadClientConfiguration {
  @Bean
  public DownloadClient downloadClient() {
    return new DefaultDownloadClient();
  }
}
