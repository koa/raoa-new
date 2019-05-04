package ch.bergturbenthal.raoa.fileuploader.server.test;

import ch.bergturbenthal.raoa.fileuploader.server.EnableFileUploadServerService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableFileUploadServerService
@SpringBootApplication
public class TestServer {
  public static void main(final String[] args) {
    SpringApplication.run(TestServer.class, args);
  }
}
