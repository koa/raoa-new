package ch.bergturbenthal.raoa.fileuploader.server.test.interfaces;

import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService.UploadingFileSlot;
import ch.bergturbenthal.raoa.service.file.upload.test.RequestFileUploadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileUploadRequest;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileUploadResponse;
import io.grpc.stub.StreamObserver;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;

@Slf4j
@GRpcService
public class UploadTestService
    extends RequestFileUploadServiceGrpc.RequestFileUploadServiceImplBase {

  private final FileuploadService fileuploadService;

  public UploadTestService(final FileuploadService fileuploadService) {
    this.fileuploadService = fileuploadService;
  }

  @Override
  public void requestFileUpload(
      final FileUploadRequest request, final StreamObserver<FileUploadResponse> responseObserver) {
    final long filesize = request.getFilesize();
    final String filename = request.getFilename();
    final UploadingFileSlot upload = fileuploadService.anounceUpload(filesize, ".tmp");

    upload
        .getData()
        .subscribe(
            data -> {
              try {
                final File file = data.getFile();
                file.renameTo(new File("/tmp", filename));
                data.close();
              } catch (final Exception e) {
                throw new RuntimeException("Cannot finish upload", e);
              }
            });

    final FileUploadResponse response =
        FileUploadResponse.newBuilder().setUploadHandle(upload.getHandle()).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
