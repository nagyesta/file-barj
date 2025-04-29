package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Augments {@link OutputStream} with the ability to perform a one-time action when it is closed for
 * the first time. Subsequent close calls will not perform the action again.
 */
public abstract class DoOnCloseOutputStream extends OutputStream {
    private final ReentrantLock closeLock = new ReentrantLock();
    @Getter
    private boolean closed = false;
    private boolean hasAnythingToFlush = false;

    @Override
    public void write(final int b) throws IOException {
        throwExceptionIfClosed();
        getOutputStream().write(b);
        hasAnythingToFlush = true;
    }

    @Override
    public void write(final byte @NotNull [] b) throws IOException {
        throwExceptionIfClosed();
        getOutputStream().write(b);
        hasAnythingToFlush = true;
    }

    @Override
    public void write(
            final byte @NotNull [] b,
            final int off,
            final int len) throws IOException {
        throwExceptionIfClosed();
        getOutputStream().write(b, off, len);
        hasAnythingToFlush = true;
    }

    @Override
    public void flush() throws IOException {
        if (!hasAnythingToFlush) {
            return;
        }
        throwExceptionIfClosed();
        getOutputStream().flush();
        hasAnythingToFlush = false;
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
            flush();
            getOutputStream().close();
            doOnClose();
            closed = true;
        } finally {
            closeLock.unlock();
        }
    }

    /**
     * Gets the output stream where the data should be written initially in order to process it
     * through the whole pipeline.
     *
     * @return the output stream
     */
    protected abstract @NotNull OutputStream getOutputStream();

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
