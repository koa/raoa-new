package ch.bergturbenthal.raoa.fileuploader.server.domain.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

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
        private Runnable        refreshTimerRunnable;
    }

    private final Map<Long, UploadJob>     currentPendingJobs = Collections.synchronizedMap(new HashMap<>());

    private final Random                   random             = new SecureRandom();

    private final ScheduledExecutorService executorService;

    public DefaultUploadService(final ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public UploadingFileSlot anounceUpload(final long expectedSize, final String suffix) {

        try {
            final UploadJob uploadJob = new UploadJob();

            final File tempFile = File.createTempFile("upload", suffix);

            uploadJob.setTargetFile(tempFile);
            uploadJob.setOutputStream(new FileOutputStream(tempFile));
            uploadJob.setUploading(true);
            uploadJob.setExpectedSize(expectedSize);
            uploadJob.setUploadTimeout(Instant.now().plus(20, ChronoUnit.SECONDS));
            uploadJob.setProcessTimeout(Instant.now().plus(30, ChronoUnit.SECONDS));

            while (true) {
                final long uploadHandle = random.nextLong();

                if (currentPendingJobs.putIfAbsent(uploadHandle, uploadJob) != null) {
                    continue;
                }
                final Runnable cleanupRunnable = () -> {
                    currentPendingJobs.remove(uploadHandle, uploadJob);
                    uploadJob.getTargetFile().delete();
                };
                final AtomicReference<ScheduledFuture<?>> pendingScheduledFuture = new AtomicReference<ScheduledFuture<?>>(null);
                final Consumer<ScheduledFuture<?>> scheduledFutureConsumer = scheduledFuture -> {
                    final ScheduledFuture<?> oldFuture = pendingScheduledFuture.getAndSet(scheduledFuture);
                    if (oldFuture != null && !oldFuture.isDone()) {
                        oldFuture.cancel(false);
                    }
                };
                final Runnable refreshTimeout = () -> {
                    final Instant timeout;
                    if (uploadJob.isUploading()) {
                        timeout = uploadJob.getUploadTimeout();
                    } else {
                        timeout = uploadJob.getProcessTimeout();
                    }
                    final long remainingTime = Duration.between(Instant.now(), timeout).toMillis();
                    if (remainingTime <= 0) {
                        cleanupRunnable.run();
                    }
                    scheduledFutureConsumer.accept(executorService.schedule(cleanupRunnable, remainingTime, TimeUnit.MILLISECONDS));
                };
                uploadJob.setRefreshTimerRunnable(refreshTimeout);

                return new UploadingFileSlot() {

                    @Override
                    public Mono<UploadedFile> getData() {
                        return Mono.create(monoSink -> {
                            uploadJob.getListeners().add(() -> {
                                final UploadedFile uploadedFile = new UploadedFile() {

                                    @Override
                                    public void close() {
                                        cleanupRunnable.run();
                                        scheduledFutureConsumer.accept(null);
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
                        if (!uploadJob.isUploading()) {
                            refreshTimeout.run();
                        }
                    }

                    @Override
                    public void setUploadTimeout(final Instant timeout) {
                        uploadJob.setUploadTimeout(timeout);
                        if (uploadJob.isUploading()) {
                            refreshTimeout.run();
                        }
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
            job.getRefreshTimerRunnable().run();
            drain(job);
        }
        return true;
    }

}
