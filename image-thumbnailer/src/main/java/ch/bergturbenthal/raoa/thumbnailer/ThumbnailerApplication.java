package ch.bergturbenthal.raoa.thumbnailer;

import ch.bergturbenthal.raoa.filedownloader.server.EnableFileDownloadService;
import ch.bergturbenthal.raoa.fileuploader.server.EnableFileUploadServerService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@EnableFileUploadServerService
@EnableFileDownloadService
@SpringBootApplication
public class ThumbnailerApplication {
  public static void main(final String[] args) {
    SpringApplication.run(ThumbnailerApplication.class, args);
  }

  @Bean
  public ScheduledExecutorService scheduledExecutorService() {
    return Executors.newScheduledThreadPool(5);
  }
}
