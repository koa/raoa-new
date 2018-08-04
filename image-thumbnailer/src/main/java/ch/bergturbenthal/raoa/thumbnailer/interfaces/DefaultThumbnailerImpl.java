package ch.bergturbenthal.raoa.thumbnailer.interfaces;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;

import org.lognet.springboot.grpc.GRpcService;

import com.google.protobuf.Empty;

import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService;
import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService.DownloadDataSource;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService.UploadingFileSlot;
import ch.bergturbenthal.raoa.service.thumbnailer.FileGeneratedMessage;
import ch.bergturbenthal.raoa.service.thumbnailer.GenerationRequest;
import ch.bergturbenthal.raoa.service.thumbnailer.GenerationResponse;
import ch.bergturbenthal.raoa.service.thumbnailer.ServiceOverloadedResponse;
import ch.bergturbenthal.raoa.service.thumbnailer.ThumbnailerCapabilities;
import ch.bergturbenthal.raoa.service.thumbnailer.ThumbnailerServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class DefaultThumbnailerImpl extends ThumbnailerServiceGrpc.ThumbnailerServiceImplBase {

    private final FileuploadService fileuploadService;
    private final DownloadService   downloadService;
    private final Semaphore         throttleSemaphore = new Semaphore(4);

    public DefaultThumbnailerImpl(final FileuploadService fileuploadService, final DownloadService downloadService) {
        this.fileuploadService = fileuploadService;
        this.downloadService = downloadService;
    }

    @Override
    public void generateThumbnail(final GenerationRequest request, final StreamObserver<GenerationResponse> responseObserver) {
        if (!throttleSemaphore.tryAcquire()) {
            responseObserver.onNext(GenerationResponse.newBuilder().setOverloadedResponse(ServiceOverloadedResponse.newBuilder()).build());
            responseObserver.onCompleted();
        }
        final long length = request.getLength();
        final String filename = request.getFilename();
        final UploadingFileSlot uploadingFileSlot = fileuploadService.anounceUpload(length, ".jpg");
        uploadingFileSlot.setUploadTimeout(Instant.now().plus(1, ChronoUnit.MINUTES));
        uploadingFileSlot.getData().subscribe(data -> {
            try {
                final File sourceFile = data.getFile();
                final File targetTempFile = File.createTempFile("out", ".jpg");
                final File logfile = File.createTempFile("out", ".log");
                final Process scaleDownprocess = new ProcessBuilder("convert", sourceFile.getAbsolutePath(), "-auto-orient", "-resize", "1600x1600",
                        targetTempFile.getAbsolutePath()).redirectOutput(logfile).start();
                // create thread to supervise external process
                new Thread(() -> {
                    try {
                        final int resultCode = scaleDownprocess.waitFor();
                        if (resultCode == 0) {
                            final long fileHandle = downloadService.anounceFile(new DownloadDataSource() {

                                @Override
                                public void downloadFinished() {
                                    targetTempFile.delete();
                                }

                                @Override
                                public InputStream getInputStream() throws IOException {
                                    return new FileInputStream(targetTempFile);
                                }
                            });
                            logfile.delete();
                            final FileGeneratedMessage fileGeneratedMessage = FileGeneratedMessage.newBuilder().setFileDownloadHandle(fileHandle)
                                    .setFileSize(targetTempFile.length()).build();
                            responseObserver.onNext(GenerationResponse.newBuilder().setFileReadyResponse(fileGeneratedMessage).build());
                        } else {
                            targetTempFile.delete();
                            // TODO: handle error
                        }
                    } catch (final InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } finally {
                        data.close();
                        responseObserver.onCompleted();
                        throttleSemaphore.release();
                    }
                }).start();
            } catch (final IOException e) {
                log.error("Cannot scale image " + data.getFile(), e);
                // TODO: send error to caller
                data.close();
                responseObserver.onCompleted();
                throttleSemaphore.release();
            }
        });

        final GenerationResponse response = GenerationResponse.newBuilder().setFileUploadHandle(uploadingFileSlot.getHandle()).build();
        responseObserver.onNext(response);
    }

    @Override
    public void getCapabilities(final Empty request, final StreamObserver<ThumbnailerCapabilities> responseObserver) {
        final ThumbnailerCapabilities capabilities = ThumbnailerCapabilities.newBuilder().addRegex(".*\\.JPG").build();
        responseObserver.onNext(capabilities);
        responseObserver.onCompleted();
    }

}
