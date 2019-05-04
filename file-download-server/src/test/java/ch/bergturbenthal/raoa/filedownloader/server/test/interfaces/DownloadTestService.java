package ch.bergturbenthal.raoa.filedownloader.server.test.interfaces;

import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService;
import ch.bergturbenthal.raoa.service.file.upload.test.RequestFileDownloadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileDownloadRequest;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileDownloadResponse;
import io.grpc.stub.StreamObserver;
import java.io.FileInputStream;
import org.lognet.springboot.grpc.GRpcService;

@GRpcService
public class DownloadTestService
    extends RequestFileDownloadServiceGrpc.RequestFileDownloadServiceImplBase {

  private final DownloadService downloadService;

  public DownloadTestService(final DownloadService downloadService) {
    this.downloadService = downloadService;
  }

  @Override
  public void requestFileDownload(
      final FileDownloadRequest request,
      final StreamObserver<FileDownloadResponse> responseObserver) {
    final long fileHandle = downloadService.anounceFile(() -> new FileInputStream("pom.xml"));
    responseObserver.onNext(
        FileDownloadResponse.newBuilder().setDownloadHandle(fileHandle).build());
    responseObserver.onCompleted();
  }
}
