package ch.bergturbenthal.raoa.thumbnailer.interfaces;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.lognet.springboot.grpc.GRpcService;

import com.google.protobuf.Empty;

import ch.bergturbenthal.raoa.service.thumbnailer.Thumbnailer.GenerationRequest;
import ch.bergturbenthal.raoa.service.thumbnailer.Thumbnailer.GenerationResponse;
import ch.bergturbenthal.raoa.service.thumbnailer.Thumbnailer.ThumbnailerCapabilities;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService.UploadingFileSlot;
import ch.bergturbenthal.raoa.service.thumbnailer.ThumbnailerServiceGrpc;
import io.grpc.stub.StreamObserver;

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
            data.getFile();
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
