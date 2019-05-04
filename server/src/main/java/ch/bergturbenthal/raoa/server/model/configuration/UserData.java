package ch.bergturbenthal.raoa.server.model.configuration;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.Optional;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class UserData {
  @NonNull
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Instant lastAccess;

  @NonNull
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Instant createdAt;

  private boolean admin;
  @NonNull private Optional<String> localPassword;
  @NonNull private AccessLevel globalAccessLevel;
}
