package ch.bergturbenthal.raoa.thumbnailer.interfaces;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.lognet.springboot.grpc.GRpcService;

import com.google.protobuf.Empty;

import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService.UploadingFileSlot;
import ch.bergturbenthal.raoa.service.thumbnailer.GenerationRequest;
import ch.bergturbenthal.raoa.service.thumbnailer.GenerationResponse;
import ch.bergturbenthal.raoa.service.thumbnailer.ThumbnailerCapabilities;
import ch.bergturbenthal.raoa.service.thumbnailer.ThumbnailerServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class DefaultThumbnailerImpl extends ThumbnailerServiceGrpc.ThumbnailerServiceImplBase {

    private final FileuploadService fileuploadService;

    public DefaultThumbnailerImpl(final FileuploadService fileuploadService) {
        this.fileuploadService = fileuploadService;
    }

    @Override
    public void claimThumbnailGeneration(final GenerationRequest request, final StreamObserver<GenerationResponse> responseObserver) {
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
                new Thread(() -> {
                    try {
                        final int resultCode = scaleDownprocess.waitFor();
                        if (resultCode == 0) {
                            // TODO: send data back
                        } else {
                            // TODO: handle error
                        }
                    } catch (final InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } finally {
                        data.close();
                    }
                }).start();
            } catch (final IOException e) {
                log.error("Cannot scale image " + data.getFile(), e);
                // TODO: send error to caller
                data.close();
            }
        });

        final GenerationResponse response = GenerationResponse.newBuilder().setFileTransferHandle(uploadingFileSlot.getHandle()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getCapabilities(final Empty request, final StreamObserver<ThumbnailerCapabilities> responseObserver) {
        final ThumbnailerCapabilities capabilities = ThumbnailerCapabilities.newBuilder().addRegex(".*\\.JPG").build();
        responseObserver.onNext(capabilities);
        responseObserver.onCompleted();
    }

}
