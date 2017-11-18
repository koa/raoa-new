package ch.bergturbenthal.raoa.server.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import ch.bergturbenthal.raoa.server.configuration.RaoaProperties;
import ch.bergturbenthal.raoa.server.model.configuration.GlobalConfigurationData;
import ch.bergturbenthal.raoa.server.service.RuntimeConfigurationService;

@Service
public class DefaultRuntimeConfigurationService implements RuntimeConfigurationService {
    private final File         configurationBase;
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public DefaultRuntimeConfigurationService(final RaoaProperties properties, final ObjectMapper objectMapper) {
        configurationBase = properties.getConfigurationBase();
        if (!configurationBase.exists()) {
            configurationBase.mkdirs();
        }
        reader = objectMapper.readerFor(GlobalConfigurationData.class);
        writer = objectMapper.writerFor(GlobalConfigurationData.class).with(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public synchronized void editGlobalConfiguration(final Function<GlobalConfigurationData, GlobalConfigurationData> configEditor) {
        final GlobalConfigurationData oldConfiguration = getGlobalConfiguration();
        final GlobalConfigurationData newConfiguration = configEditor.apply(oldConfiguration);
        if (oldConfiguration.equals(newConfiguration)) {
            return;
        }
        updateGlobalConfiguration(newConfiguration);
    }

    @Override
    public GlobalConfigurationData getGlobalConfiguration() {
        final File configFile = globalConfigFile();
        if (!configFile.exists()) {
            return GlobalConfigurationData.builder().knownUsers(Collections.emptyMap()).build();
        }
        try {
            return reader.readValue(configFile);
        } catch (final IOException e) {
            throw new RuntimeException("Cannot read config", e);
        }
    }

    private File globalConfigFile() {
        return new File(configurationBase, "config.json");
    }

    private void updateGlobalConfiguration(final GlobalConfigurationData data) {
        try {
            final File tempFile = File.createTempFile("config", ".json", configurationBase);
            writer.writeValue(tempFile, data);
            tempFile.renameTo(globalConfigFile());
        } catch (final IOException e) {
            throw new RuntimeException("Cannot write config", e);
        }
    }

}
