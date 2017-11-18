package ch.bergturbenthal.raoa.server.service;

import java.util.function.Function;

import ch.bergturbenthal.raoa.server.model.configuration.GlobalConfigurationData;

public interface RuntimeConfigurationService {
    void editGlobalConfiguration(Function<GlobalConfigurationData, GlobalConfigurationData> configEditor);

    GlobalConfigurationData getGlobalConfiguration();
}
