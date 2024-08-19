package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoFunction;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link InputStream} ensuring the execution of an optional list of transformation steps and a
 * size calculation step and an optional digest calculation step on the streamed content.
 */
public class CompositeRestoreStream extends DoOnCloseInputStream {

    private final InputStream sourceStream;
    private final SelfValidatingOptionalDigestInputStream digestInputStream;
    private final List<InputStream> transformationStreams;
    private final BufferedInputStream bufferedStream;

    /**
     * Decorates the provided source stream with this composite stream using the provided list of
     * transformations, as well as size and digest calculation.
     *
     * @param sourceStream            The destination where this  stream
     *                                should write.
     * @param digestAlgorithm         The algorithm we should use for
     *                                digest calculation.
     * @param transformationFunctions The series of transformation we need to perform on the content
     *                                before reading it from the source stream.
     * @param expectedDigest          The expected digest of the decrypted and decompressed data once
     *                                read fully.
     * @throws IOException When the stream cannot be decorated.
     */
    public CompositeRestoreStream(final @NonNull InputStream sourceStream,
                                  final @Nullable String digestAlgorithm,
                                  final @NonNull List<IoFunction<InputStream, InputStream>> transformationFunctions,
                                  final @Nullable String expectedDigest) throws IOException {
        SelfValidatingOptionalDigestInputStream dis = null;
        BufferedInputStream bis = null;
        final List<InputStream> ts = new ArrayList<>();
        try {
            var source = sourceStream;
            for (final var transformation : transformationFunctions) {
                final var decorated = transformation.decorate(source);
                ts.add(decorated);
                source = decorated;
            }
            dis = new SelfValidatingOptionalDigestInputStream(source, digestAlgorithm, expectedDigest);
            bis = new BufferedInputStream(dis);
            this.sourceStream = sourceStream;
            this.transformationStreams = ts;
            this.digestInputStream = dis;
            this.bufferedStream = bis;
        } catch (final Exception e) {
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(dis);
            ts.forEach(IOUtils::closeQuietly);
            IOUtils.closeQuietly(sourceStream);
            throw e;
        }
    }

    @Override
    protected @NotNull @NonNull InputStream getInputStream() {
        return bufferedStream;
    }

    @Override
    protected void doOnClose() {
        IOUtils.closeQuietly(bufferedStream);
        IOUtils.closeQuietly(digestInputStream);
        transformationStreams.forEach(IOUtils::closeQuietly);
        IOUtils.closeQuietly(sourceStream);
    }

}
