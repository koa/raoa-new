package ch.bergturbenthal.raoa.server.model.configuration;

import java.util.Map;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class GlobalConfigurationData {
    @NonNull
    private Map<String, UserData> knownUsers;
}
