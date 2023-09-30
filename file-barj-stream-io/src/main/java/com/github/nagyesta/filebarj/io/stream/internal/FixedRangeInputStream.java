package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Allows us to read a fixed range of bytes from an input stream and act as if the end of the stream
 * was reached when the range is exhausted.
 */
public class FixedRangeInputStream extends CountingInputStream {

    private final long endExclusive;

    /**
     * Creates a new instance.
     *
     * @param source         the source stream
     * @param startInclusive the start of the range
     * @param length         the length of the range
     * @throws IOException if the source stream cannot be read
     */
    public FixedRangeInputStream(
            @NonNull final InputStream source, final long startInclusive, final long length)
            throws IOException {
        super(source);
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("startInclusive must be >= 0");
        }
        this.endExclusive = startInclusive + length;
        skipNBytes(startInclusive);
    }

    @Override
    public int read() throws IOException {
        if (getByteCount() >= endExclusive) {
            return IOUtils.EOF;
        }
        return super.read();
    }

    @Override
    public int read(final byte[] bts) throws IOException {
        return read(bts, 0, bts.length);
    }

    @Override
    public int read(final byte[] bts, final int off, final int len) throws IOException {
        if (getByteCount() >= endExclusive) {
            return IOUtils.EOF;
        }
        final var allowedLength = endExclusive - getByteCount();
        return super.read(bts, off, (int) Math.min(len, allowedLength));
    }
}
