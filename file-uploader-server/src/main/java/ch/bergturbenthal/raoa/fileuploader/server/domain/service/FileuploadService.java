package ch.bergturbenthal.raoa.fileuploader.server.domain.service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import reactor.core.publisher.Mono;

public interface FileuploadService {
    public static interface UploadedFile extends AutoCloseable {
        @Override
        void close();

        File getFile();

    }

    public static interface UploadingFileSlot {

        Mono<UploadedFile> getData();

        long getHandle();

        void setProcessTimeout(Instant timeout);

        void setUploadTimeout(Instant timeout);
    }

    UploadingFileSlot anounceUpload(long expectedSize, String suffix);

    boolean uploadFragment(long handle, byte[] data) throws IOException;
}
