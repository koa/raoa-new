package ch.bergturbenthal.raoa.fileuploader.server.interfaces;

import java.io.IOException;

import org.lognet.springboot.grpc.GRpcService;

import com.google.protobuf.ByteString;

import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.service.file.upload.FileUploadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.FragmentRequest;
import ch.bergturbenthal.raoa.service.file.upload.FragmentResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class DefaultFileUploaderService extends FileUploadServiceGrpc.FileUploadServiceImplBase {
    private final FileuploadService fileuploadService;

    public DefaultFileUploaderService(final FileuploadService fileuploadService) {
        this.fileuploadService = fileuploadService;
    }

    @Override
    public void sendFragment(final FragmentRequest request, final StreamObserver<FragmentResponse> responseObserver) {
        final long sendHandle = request.getSendHandle();
        final ByteString data = request.getData();
        final FragmentResponse.Builder responseBuilder = FragmentResponse.newBuilder();
        try {
            final boolean success = fileuploadService.uploadFragment(sendHandle, data.toByteArray());
            responseBuilder.setState(success ? FragmentResponse.ResponseState.OK : FragmentResponse.ResponseState.UNKNOWN_HANDLE);
        } catch (final IOException e) {
            log.warn("Cannot upload fragment", e);
            responseBuilder.setState(FragmentResponse.ResponseState.TEMPORARY_ERROR);
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}
