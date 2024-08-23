package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation for {@link OutputStream} that can write the data into multiple streams with a
 * predefined maximum size per part.
 */
public abstract class ChunkingOutputStream extends OutputStream {
    /**
     * The number of bytes per mebibyte.
     */
    public static final long MEBIBYTE = 1024L * 1024L;
    private static final int BUFFER_SIZE = 16 * 1024;
    @Getter
    private final long maxChunkSizeBytes;
    private long currentByteCount = 0L;
    private long byteCountOffset = 0L;
    private OutputStream currentStream;
    private final ReentrantLock thresholdLock = new ReentrantLock();

    /**
     * Creates a new instance and sets the parameters needed for chunked write operations.
     *
     * @param maxFileSizeMebibyte The maximum chunk size in mebibyte
     */
    public ChunkingOutputStream(final int maxFileSizeMebibyte) {
        this.maxChunkSizeBytes = maxFileSizeMebibyte * MEBIBYTE;
    }

    /**
     * Returns the total number of bytes written to the output so far.
     *
     * @return the total number of bytes
     */
    public long getTotalByteCount() {
        return byteCountOffset + getChunkRelativeByteCount();
    }

    /**
     * Returns the number of bytes written to the current chunk so far.
     *
     * @return the number of bytes in the chunk
     */
    public long getChunkRelativeByteCount() {
        return currentByteCount;
    }

    @Override
    public void write(final int b) throws IOException {
        enforceThreshold(1);
        currentStream.write(b);
        currentByteCount++;
    }

    @Override
    public void write(final byte @NotNull [] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
        for (var i = 0; i < len; i += BUFFER_SIZE) {
            final var bufferedSize = Math.min(BUFFER_SIZE, len - i);
            //write what we can before the threshold
            final var lenBefore = bytesUntilThreshold(bufferedSize);
            doWrite(b, off + i, lenBefore);
            //calculate remaining and continue after the threshold
            final var offAfter = off + i + lenBefore;
            final var lenAfter = bufferedSize - lenBefore;
            doWrite(b, offAfter, lenAfter);
        }
    }

    @Override
    public void flush() throws IOException {
        currentStream.flush();
    }

    @Override
    public void close() throws IOException {
        currentStream.close();
    }

    /**
     * Switches from the current chunk to the next one.
     *
     * @return the next stream
     * @throws IOException if we cannot open the next stream
     */
    protected final @NotNull OutputStream openNextStream() throws IOException {
        final var byteCount = currentByteCount;
        byteCountOffset = byteCountOffset + byteCount;
        currentByteCount = 0L;
        currentStream = doOpenNextStream();
        return currentStream;
    }

    /**
     * Opens the stream for the next chunk.
     *
     * @return the next stream
     * @throws IOException if we cannot open the next stream
     */
    protected abstract @NotNull OutputStream doOpenNextStream() throws IOException;

    private void doWrite(final byte @NotNull [] b, final int off, final int len) throws IOException {
        if (len <= 0) {
            return;
        }
        enforceThreshold(len);
        currentStream.write(b, off, len);
        currentByteCount += len;
    }

    private void enforceThreshold(final int len) throws IOException {
        thresholdLock.lock();
        try {
            if (currentStream == null) {
                currentStream = openNextStream();
            }
            if (maxChunkSizeBytes < currentByteCount + len) {
                currentStream.flush();
                currentStream.close();
                currentStream = openNextStream();
            }
        } finally {
            thresholdLock.unlock();
        }
    }

    private int bytesUntilThreshold(final int max) {
        final var remaining = Math.max(0L, maxChunkSizeBytes - currentByteCount);
        return (int) Math.min(remaining, max);
    }
}
