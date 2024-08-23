package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoFunction;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} implementation designed for archiving entities by executing a set of
 * preconfigured steps in order, such as digest calculation, compression and encryption.
 */
public class ArchiveEntryOutputStream extends DoOnCloseOutputStream {
    private final BaseBarjCargoArchiverFileOutputStream destinationStream;
    private final CompositeArchiveStream originalDigestStream;
    private final CompositeArchiveStream compressionStream;
    private final CompositeArchiveStream encryptionStream;
    private final BarjCargoEntryBoundaries.BarjCargoEntryBoundariesBuilder boundariesBuilder;
    private BarjCargoEntryBoundaries boundaries;

    /**
     * Creates a new Stream instance writing to the destination stream and using the provided
     * function to create a stream that will encrypt the content.
     *
     * @param destinationStream  The destination where the new stream should write.
     * @param encryptionFunction The function that can create a Stream which will perform the
     *                           desired encryption.
     * @throws IOException When the stream cannot be decorated.
     */
    public ArchiveEntryOutputStream(
            final @NonNull BaseBarjCargoArchiverFileOutputStream destinationStream,
            final @NonNull IoFunction<OutputStream, OutputStream> encryptionFunction) throws IOException {
        this.destinationStream = destinationStream;

        // save boundary information before anything could interfere with the stream
        this.boundariesBuilder = BarjCargoEntryBoundaries.builder()
                .startChunkName(destinationStream.getCurrentFilePath().getFileName().toString())
                .absoluteStartIndexInclusive(destinationStream.getTotalByteCount())
                .chunkRelativeStartIndexInclusive(destinationStream.getChunkRelativeByteCount());

        //instantiate streams

        CloseShieldingOutputStream css = null;
        CompositeArchiveStream es = null;
        CompositeArchiveStream cs = null;
        CompositeArchiveStream ds = null;
        try {
            css = new CloseShieldingOutputStream(destinationStream);
            es = new CompositeArchiveStream(css, destinationStream.getHashAlgorithm(), encryptionFunction);
            cs = new CompositeArchiveStream(es, null, destinationStream.getCompressionFunction());
            ds = new CompositeArchiveStream(cs, destinationStream.getHashAlgorithm());
            this.encryptionStream = es;
            this.compressionStream = cs;
            this.originalDigestStream = ds;
        } catch (final Exception e) {
            IOUtils.closeQuietly(ds);
            IOUtils.closeQuietly(cs);
            IOUtils.closeQuietly(es);
            IOUtils.closeQuietly(css);
            throw e;
        }
    }

    /**
     * Returns the previously calculated boundaries of the entry. Must be called on a closed stream.
     *
     * @return the calculated boundaries
     */
    public BarjCargoEntryBoundaries getEntityBoundary() {
        assertClosed();
        return boundaries;
    }

    @Override
    protected @NotNull OutputStream getOutputStream() {
        return originalDigestStream;
    }

    @Override
    protected void doOnClose() {
        IOUtils.closeQuietly(originalDigestStream);
        IOUtils.closeQuietly(compressionStream);
        IOUtils.closeQuietly(encryptionStream);
        this.boundariesBuilder.absoluteEndIndexExclusive(destinationStream.getTotalByteCount())
                .chunkRelativeEndIndexExclusive(destinationStream.getChunkRelativeByteCount())
                .endChunkName(destinationStream.getCurrentFilePath().getFileName().toString())
                .originalHash(originalDigestStream.getDigestValue())
                .originalSizeBytes(originalDigestStream.getByteCount())
                .archivedHash(encryptionStream.getDigestValue())
                .archivedSizeBytes(encryptionStream.getByteCount());
        this.boundaries = boundariesBuilder.build();
    }
}
