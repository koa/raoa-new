package ch.bergturbenthal.raoa.server.service;

import ch.bergturbenthal.raoa.server.model.configuration.GlobalConfigurationData;
import java.util.function.Function;

public interface RuntimeConfigurationService {
  void editGlobalConfiguration(
      Function<GlobalConfigurationData, GlobalConfigurationData> configEditor);

  GlobalConfigurationData getGlobalConfiguration();
}
