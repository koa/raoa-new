package ch.bergturbenthal.raoa.filedownloader.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ch.bergturbenthal.raoa.filedownloader.server.EnableFileDownloadService;

@EnableFileDownloadService
@SpringBootApplication
public class TestServer {
    public static void main(final String[] args) {
        SpringApplication.run(TestServer.class, args);
    }
}
