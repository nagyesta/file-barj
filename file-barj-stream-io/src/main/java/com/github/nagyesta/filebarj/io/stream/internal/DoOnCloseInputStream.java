package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Augments {@link InputStream} with the ability to perform a one-time action when it is closed for
 * the first time. Subsequent close calls will not perform the action again.
 */
public abstract class DoOnCloseInputStream extends InputStream {
    private final ReentrantLock closeLock = new ReentrantLock();
    @Getter
    private boolean closed = false;

    @Override
    public int read() throws IOException {
        throwExceptionIfClosed();
        return getInputStream().read();
    }

    @Override
    public int read(final byte @NotNull [] b) throws IOException {
        throwExceptionIfClosed();
        return getInputStream().read(b);
    }

    @Override
    public int read(
            final byte @NotNull [] b,
            final int off,
            final int len) throws IOException {
        throwExceptionIfClosed();
        return getInputStream().read(b, off, len);
    }

    @Override
    @SuppressWarnings("java:S2583") //the method may be called by multiple threads
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            getInputStream().close();
            doOnClose();
            closed = true;
        } finally {
            closeLock.unlock();
        }
    }

    /**
     * Gets the input stream where the data should be read from initially in order to process it
     * through the whole pipeline.
     *
     * @return the input stream
     */
    protected abstract @NotNull InputStream getInputStream();

    /**
     * Performs some action exactly once when the close method is first called on the stream.
     */
    protected abstract void doOnClose();

    /**
     * Verifies that the stream is in fact closed already.
     *
     * @throws IllegalStateException if the stream was not closed yet.
     */
    protected void assertClosed() {
        if (!closed) {
            throw new IllegalStateException("Stream is not closed yet! Close it first to get this value.");
        }
    }

    private void throwExceptionIfClosed() throws IOException {
        if (closed) {
            throw new IOException("Stream is already closed.");
        }
    }
}
