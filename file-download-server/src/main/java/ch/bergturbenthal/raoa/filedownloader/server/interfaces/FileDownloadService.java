package ch.bergturbenthal.raoa.filedownloader.server.interfaces;

import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService;
import ch.bergturbenthal.raoa.service.file.download.FileDownloadRequest;
import ch.bergturbenthal.raoa.service.file.download.FileDownloadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.download.FileFragment;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

@GRpcService
public class FileDownloadService extends FileDownloadServiceGrpc.FileDownloadServiceImplBase {

  private final DownloadService downloadService;

  public FileDownloadService(final DownloadService downloadService) {
    this.downloadService = downloadService;
  }

  @Override
  public void downloadFile(
      final FileDownloadRequest request, final StreamObserver<FileFragment> responseObserver) {
    final long handle = request.getDownloadHandle();
    downloadService
        .downloadFile(handle)
        .subscribe(
            responseObserver::onNext, responseObserver::onError, responseObserver::onCompleted);
  }
}
