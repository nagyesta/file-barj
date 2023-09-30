package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoSupplier;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reads data previously chunked by a {@link ChunkingOutputStream}.
 */
public class MergingInputStream extends InputStream {
    private static final long UNKNOWN = -1L;
    private final Iterator<IoSupplier<InputStream>> chunkIterator;
    private long remainingBytes;
    private InputStream currentStream;
    private final ReentrantLock closeLock = new ReentrantLock();
    @Getter
    private boolean closed = false;

    /**
     * Creates a new instance and sets the parameters needed reading chunked contents.
     *
     * @param allStreams The list of all stream suppliers
     * @param totalBytes The total number of bytes to read (optional)
     * @throws IOException If we cannot read the sources.
     */
    public MergingInputStream(
            @NonNull final List<IoSupplier<InputStream>> allStreams,
            @Nullable final Long totalBytes)
            throws IOException {
        this.remainingBytes = Optional.ofNullable(totalBytes).orElse(UNKNOWN);
        this.chunkIterator = List.copyOf(allStreams).iterator();
        this.currentStream = openNextStream();
    }

    @Override
    public int read() throws IOException {
        if (closed || isEndOfLastStreamReached()) {
            return IOUtils.EOF;
        }
        var read = currentStream.read();
        if (isEndOfCurrentStreamReached(read)) {
            //noinspection resource
            openNextStream();
            if (isEndOfLastStreamReached()) {
                return IOUtils.EOF;
            }
            read = currentStream.read();
        }
        reduceRemainingBytes(1);
        return read;
    }

    @Override
    public int read(final byte @NotNull [] result, final int offset, final int length) throws IOException {
        if (closed || isEndOfLastStreamReached()) {
            return IOUtils.EOF;
        }
        var read = currentStream.read(result, offset, length);
        if (isEndOfCurrentStreamReached(read)) {
            //noinspection resource
            openNextStream();
            if (isEndOfLastStreamReached()) {
                return IOUtils.EOF;
            }
            read = currentStream.read(result, offset, length);
        }
        reduceRemainingBytes(read);
        return read;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(estimateAvailableBytes(), Integer.MAX_VALUE);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            if (currentStream != null) {
                currentStream.close();
            }
            currentStream = null;
            closed = true;
        } finally {
            closeLock.unlock();
        }
    }

    private InputStream openNextStream() throws IOException {
        if (currentStream != null) {
            currentStream.close();
        }
        if (chunkIterator.hasNext()) {
            currentStream = new BufferedInputStream(chunkIterator.next().get());
        } else {
            currentStream = null;
            close();
        }
        return currentStream;
    }

    private long estimateAvailableBytes() throws IOException {
        if (closed) {
            return 0;
        }
        return Math.max(remainingBytes, currentStream.available());
    }

    private void reduceRemainingBytes(final long bytes) {
        if (remainingBytes > UNKNOWN) {
            remainingBytes -= bytes;
        } else {
            remainingBytes = UNKNOWN;
        }
    }

    private boolean isEndOfLastStreamReached() {
        return !chunkIterator.hasNext() && currentStream == null;
    }

    private boolean isEndOfCurrentStreamReached(final int read) {
        return read == IOUtils.EOF;
    }
}
