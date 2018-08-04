package ch.bergturbenthal.raoa.filedownloader.server.domain.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.protobuf.ByteString;

import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService;
import ch.bergturbenthal.raoa.service.file.download.FileFragment;
import lombok.Value;
import reactor.core.publisher.Flux;

public class DefaultDownloadService implements DownloadService {

    @Value
    private static class DownloadData {
        private DownloadDataSource source;
        private Runnable           finishedHandler;
    }

    private final Map<Long, DownloadData> currentPendingJobs = Collections.synchronizedMap(new HashMap<>());

    private final Random                  random             = new SecureRandom();

    @Override
    public long anounceFile(final DownloadDataSource dataSource) {
        while (true) {
            final Long handle = random.nextLong();
            final DownloadData value = new DownloadData(dataSource, () -> {
                currentPendingJobs.remove(handle);
            });
            if (currentPendingJobs.putIfAbsent(handle, value) != null) {
                continue;
            }
            return handle;
        }
    }

    @Override
    public Flux<FileFragment> downloadFile(final long handle) {
        final DownloadData data = currentPendingJobs.remove(handle);
        if (data == null) {
            throw new IllegalArgumentException("Unknown handle: " + handle);
        }
        return Flux.create(fluxSink -> {
            try {
                final DownloadDataSource source = data.getSource();
                final InputStream inputStream = source.getInputStream();
                final byte[] buffer = new byte[1024 * 1024];
                while (true) {
                    int ptr = 0;
                    while (ptr < buffer.length) {
                        final int read = inputStream.read(buffer, ptr, buffer.length - ptr);
                        if (read < 0) {
                            break;
                        }
                        ptr += read;
                    }
                    final ByteString byteString = ByteString.copyFrom(buffer, 0, ptr);
                    final boolean isLastFragment = ptr < buffer.length;
                    fluxSink.next(FileFragment.newBuilder().setData(byteString).setLastFragment(isLastFragment).build());
                    if (isLastFragment) {
                        break;
                    }
                }
            } catch (final IOException e) {
                fluxSink.error(e);
            } finally {
                fluxSink.complete();
            }
        });
    }

}
