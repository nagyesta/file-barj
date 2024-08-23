package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoFunction;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} ensuring the execution of an optional transformation step, a size
 * calculation step and an optional digest calculation step on the streamed content.
 */
public class CompositeArchiveStream extends DoOnCloseOutputStream {

    private final OutputStream destinationStream;
    private final OptionalDigestOutputStream digestOutputStream;
    private final CountingOutputStream countingOutputStream;
    private final OutputStream transformationStream;
    private Long byteCount;
    private String digestValue;

    /**
     * Decorates the provided destination stream with this composite stream without using any
     * transformation.
     *
     * @param destinationStream The destination where this  stream should write.
     * @param digestAlgorithm   The algorithm we should use for digest calculation.
     * @throws IOException When the stream cannot be decorated.
     */
    public CompositeArchiveStream(final @NotNull OutputStream destinationStream,
                                  final @Nullable String digestAlgorithm) throws IOException {
        this(destinationStream, digestAlgorithm, IoFunction.IDENTITY_OUTPUT_STREAM);
    }

    /**
     * Decorates the provided destination stream with this composite stream using the provided
     * transformation. The transformation will be applied before the digest and size calculation.
     *
     * @param destinationStream The destination where this  stream should write.
     * @param digestAlgorithm   The algorithm we should use for digest calculation.
     * @param transformation    The transformation we need to perform on the content before writing
     *                          it to the destination.
     * @throws IOException When the stream cannot be decorated.
     */
    public CompositeArchiveStream(final @NonNull OutputStream destinationStream,
                                  final @Nullable String digestAlgorithm,
                                  final @NonNull IoFunction<OutputStream, OutputStream> transformation) throws IOException {
        OptionalDigestOutputStream dos = null;
        CountingOutputStream cos = null;
        OutputStream ts = null;
        try {
            dos = new OptionalDigestOutputStream(destinationStream, digestAlgorithm);
            cos = new CountingOutputStream(dos);
            ts = transformation.decorate(cos);
            this.destinationStream = destinationStream;
            this.digestOutputStream = dos;
            this.countingOutputStream = cos;
            this.transformationStream = ts;
        } catch (final Exception e) {
            IOUtils.closeQuietly(ts);
            IOUtils.closeQuietly(cos);
            IOUtils.closeQuietly(dos);
            IOUtils.closeQuietly(destinationStream);
            throw e;
        }
    }

    /**
     * Returns the calculated size value.
     *
     * @return The number of bytes written to the destination stream.
     * @throws IllegalStateException When the stream is not closed yet.
     */
    public long getByteCount() throws IllegalStateException {
        assertClosed();
        return byteCount;
    }

    /**
     * Returns the calculated digest value.
     *
     * @return The digest value. null if called with the null algorithm.
     * @throws IllegalStateException When the stream is not closed yet.
     */
    public @Nullable String getDigestValue() throws IllegalStateException {
        assertClosed();
        return digestValue;
    }

    @Override
    protected @NotNull OutputStream getOutputStream() {
        return transformationStream;
    }

    @Override
    protected void doOnClose() {
        IOUtils.closeQuietly(countingOutputStream);
        IOUtils.closeQuietly(digestOutputStream);
        IOUtils.closeQuietly(destinationStream);
        byteCount = countingOutputStream.getByteCount();
        digestValue = digestOutputStream.getDigestValue();
    }

}
