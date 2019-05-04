package ch.bergturbenthal.raoa.server;

import ch.bergturbenthal.raoa.filedownload.client.EnableFileDownloadClient;
import ch.bergturbenthal.raoa.fileuploader.client.EnableFileUploadClient;
import ch.bergturbenthal.raoa.server.security.SpringSecurityConfig;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Import({SpringSecurityConfig.class})
@EnableScheduling
@EnableFileUploadClient
@EnableFileDownloadClient
public class ServerApplication {

  public static void main(final String[] args) {
    final ConfigurableApplicationContext applicationContext =
        SpringApplication.run(ServerApplication.class, args);
  }

  @Bean
  public Executor executor() {
    return Executors.newCachedThreadPool();
  }
}
