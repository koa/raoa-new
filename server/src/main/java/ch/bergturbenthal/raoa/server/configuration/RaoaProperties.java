package ch.bergturbenthal.raoa.server.configuration;

import java.io.File;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties("raoa")
public class RaoaProperties {
    @NotNull
    private String adminEmail;
    private File   configurationBase = new File(FileUtils.getTempDirectory(), "raoa-settings");
    private File   storageBase       = new File(FileUtils.getTempDirectory(), "raoa-storage");
}
