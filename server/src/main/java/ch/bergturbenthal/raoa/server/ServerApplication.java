package ch.bergturbenthal.raoa.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import ch.bergturbenthal.raoa.server.security.SpringSecurityConfig;

@SpringBootApplication
@Import(SpringSecurityConfig.class)
public class ServerApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
