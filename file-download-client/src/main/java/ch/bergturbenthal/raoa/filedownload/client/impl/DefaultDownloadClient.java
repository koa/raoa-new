package ch.bergturbenthal.raoa.filedownload.client.impl;

import com.google.protobuf.ByteString;

import ch.bergturbenthal.raoa.filedownload.client.DownloadClient;
import ch.bergturbenthal.raoa.service.file.download.FileDownloadRequest;
import ch.bergturbenthal.raoa.service.file.download.FileDownloadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.download.FileDownloadServiceGrpc.FileDownloadServiceStub;
import ch.bergturbenthal.raoa.service.file.download.FileFragment;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;

public class DefaultDownloadClient implements DownloadClient {

    @Override
    public Flux<ByteString> downloadFile(final long handle, final Channel channel) {
        return Flux.create(fluxSink -> {
            final FileDownloadServiceStub downloadServiceStub = FileDownloadServiceGrpc.newStub(channel);
            downloadServiceStub.downloadFile(FileDownloadRequest.newBuilder().setDownloadHandle(handle).build(), new StreamObserver<FileFragment>() {

                @Override
                public void onCompleted() {
                    fluxSink.complete();
                }

                @Override
                public void onError(final Throwable t) {
                    fluxSink.error(t);
                }

                @Override
                public void onNext(final FileFragment value) {
                    fluxSink.next(value.getData());
                }
            });

        });
    }

}
