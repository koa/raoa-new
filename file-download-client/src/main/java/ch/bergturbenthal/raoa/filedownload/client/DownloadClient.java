package ch.bergturbenthal.raoa.filedownload.client;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import reactor.core.publisher.Flux;

public interface DownloadClient {
  Flux<ByteString> downloadFile(long handle, Channel channel);
}
