package ch.bergturbenthal.raoa.fileuploader.server.domain.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import lombok.Data;
import reactor.core.publisher.Mono;

public class DefaultUploadService implements FileuploadService {

    @Data
    private static class UploadJob {
        private long            expectedSize;
        private Instant         uploadTimeout;
        private Instant         processTimeout;
        private File            targetFile;
        private OutputStream    outputStream;
        private boolean         uploading;
        private LongAdder       uploadCount = new LongAdder();
        private Queue<Runnable> listeners   = new ConcurrentLinkedQueue<>();
    }

    private final Map<Long, UploadJob> currentPendingJobs = Collections.synchronizedMap(new HashMap<>());

    private final Random               random             = new SecureRandom();

    @Override
    public UploadingFileSlot anounceUpload(final long expectedSize, final String suffix) {

        try {
            final UploadJob uploadJob = new UploadJob();

            final File tempFile = File.createTempFile("upload", suffix);

            uploadJob.setTargetFile(tempFile);
            uploadJob.setOutputStream(new FileOutputStream(tempFile));
            uploadJob.setUploading(true);

            while (true) {
                final long uploadHandle = random.nextLong();

                if (currentPendingJobs.putIfAbsent(uploadHandle, uploadJob) != null) {
                    continue;
                }

                return new UploadingFileSlot() {

                    @Override
                    public void close() {
                        currentPendingJobs.remove(uploadHandle, uploadJob);
                        uploadJob.getTargetFile().delete();
                    }

                    @Override
                    public Mono<UploadedFile> getData() {
                        return Mono.create(monoSink -> {
                            uploadJob.getListeners().add(() -> {
                                final UploadedFile uploadedFile = new UploadedFile() {

                                    @Override
                                    public void close() throws Exception {
                                        currentPendingJobs.remove(uploadHandle, uploadJob);
                                        uploadJob.getTargetFile().delete();

                                    }

                                    @Override
                                    public File getFile() {
                                        return uploadJob.getTargetFile();
                                    }
                                };
                                monoSink.success(uploadedFile);
                            });
                            if (!uploadJob.isUploading()) {
                                drain(uploadJob);
                            }
                        });
                    }

                    @Override
                    public long getHandle() {
                        return uploadHandle;
                    }

                    @Override
                    public void setProcessTimeout(final Instant timeout) {
                        uploadJob.setProcessTimeout(timeout);
                    }

                    @Override
                    public void setUploadTimeout(final Instant timeout) {
                        uploadJob.setUploadTimeout(timeout);
                    }
                };
            }
        } catch (final IOException e) {
            throw new RuntimeException("prepare upload", e);
        }
    }

    private void drain(final UploadJob job) {
        final Queue<Runnable> listeners = job.getListeners();
        while (true) {
            final Runnable runnable = listeners.poll();
            if (runnable == null) {
                break;
            }
            runnable.run();
        }
    }

    @Override
    public boolean uploadFragment(final long handle, final byte[] data) throws IOException {
        final UploadJob job = currentPendingJobs.get(handle);
        if (job == null) {
            return false;
        }
        if (!job.isUploading()) {
            return false;
        }
        final OutputStream outputStream = job.getOutputStream();
        outputStream.write(data);
        final LongAdder uploadCount = job.getUploadCount();
        uploadCount.add(data.length);
        if (uploadCount.longValue() >= job.getExpectedSize()) {
            outputStream.close();
            job.setUploading(false);
            drain(job);
        }
        return true;
    }

}
