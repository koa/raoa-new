package ch.bergturbenthal.raoa.filedownloader.server.test;

import ch.bergturbenthal.raoa.filedownloader.server.EnableFileDownloadService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableFileDownloadService
@SpringBootApplication
public class TestServer {
  public static void main(final String[] args) {
    SpringApplication.run(TestServer.class, args);
  }
}
