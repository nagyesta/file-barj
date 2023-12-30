package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.NonNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream decorator avoiding accidental stream closures. Intended to be used for Zip entry
 * streams avoiding unintended close calls on the main Zip stream.
 */
public class CloseShieldingOutputStream extends OutputStream {
    private final OutputStream internal;

    /**
     * Creates a new wrapper for the given stream.
     *
     * @param stream The stream to wrap
     */
    public CloseShieldingOutputStream(@NonNull final OutputStream stream) {
        this.internal = stream;
    }

    @Override
    public void write(final int b) throws IOException {
        internal.write(b);
    }

    /**
     * Flushes the stream, but does not close it!
     *
     * @throws IOException When the flush fails
     */
    @Override
    public void close() throws IOException {
        flush();
        //don't close yet!
    }

    @Override
    public void flush() throws IOException {
        internal.flush();
    }

    /**
     * Calls the close method on the wrapped stream.
     *
     * @throws IOException When the close fails
     */
    @SuppressWarnings("unused")
    public void closeStream() throws IOException {
        flush();
        internal.close();
    }
}
