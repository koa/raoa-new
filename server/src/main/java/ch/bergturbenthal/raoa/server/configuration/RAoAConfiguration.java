package ch.bergturbenthal.raoa.server.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RAoAConfiguration {
    @Bean
    public RaoaProperties raoaProperties() {
        return new RaoaProperties();
    }
}
