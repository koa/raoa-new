package ch.bergturbenthal.raoa.fileuploader.server;

import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.impl.DefaultUploadService;
import ch.bergturbenthal.raoa.fileuploader.server.interfaces.DefaultFileUploaderService;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileUploaderServiceConfiguration {
  @Bean
  public DefaultFileUploaderService defaultFileUploaderService(
      final FileuploadService fileuploadService) {
    return new DefaultFileUploaderService(fileuploadService);
  }

  @Bean
  public FileuploadService fileuploadService(final ScheduledExecutorService executorService) {
    return new DefaultUploadService(executorService);
  }
}
