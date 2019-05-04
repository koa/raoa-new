package ch.bergturbenthal.raoa.server.model.event;

import java.io.File;
import lombok.Data;

@Data
public class StorageFoundEvent {
  private File repositoryDir;
}
