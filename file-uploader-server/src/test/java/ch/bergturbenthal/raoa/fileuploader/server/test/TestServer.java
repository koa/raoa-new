package ch.bergturbenthal.raoa.fileuploader.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ch.bergturbenthal.raoa.fileuploader.server.EnableFileUploadServerService;

@EnableFileUploadServerService
@SpringBootApplication
public class TestServer {
    public static void main(final String[] args) {
        SpringApplication.run(TestServer.class, args);
    }
}
