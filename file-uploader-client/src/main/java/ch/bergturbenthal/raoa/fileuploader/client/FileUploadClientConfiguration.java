package ch.bergturbenthal.raoa.fileuploader.client;

import ch.bergturbenthal.raoa.fileuploader.client.impl.DefaultUploadClient;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileUploadClientConfiguration {
  @Bean
  public UploaderClient uploaderClient(final Executor executor) {
    return new DefaultUploadClient(executor);
  }
}
