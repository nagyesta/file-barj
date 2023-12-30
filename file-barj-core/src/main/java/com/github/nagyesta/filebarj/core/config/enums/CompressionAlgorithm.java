package com.github.nagyesta.filebarj.core.config.enums;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The algorithm used for compression of the backup archive.
 */
public enum CompressionAlgorithm {
    /**
     * BZIP2 compression and decompression.
     */
    BZIP2 {
        @Override
        public OutputStream decorateOutputStream(final OutputStream destination)
                throws IOException {
            return new BZip2CompressorOutputStream(destination);
        }

        @Override
        public InputStream decorateInputStream(final InputStream source)
                throws IOException {
            return new BZip2CompressorInputStream(source);
        }
    },
    /**
     * GZIP compression and decompression.
     */
    GZIP {
        @Override
        public OutputStream decorateOutputStream(final OutputStream destination)
                throws IOException {
            return new GzipCompressorOutputStream(destination);
        }

        @Override
        public InputStream decorateInputStream(final InputStream source)
                throws IOException {
            return new GzipCompressorInputStream(source);
        }
    },
    /**
     * No compression.
     */
    NONE;

    /**
     * Wraps the destination stream with an optional compression layer using
     * the selected algorithm.
     *
     * @param destination The destination stream where the compressor stream
     *                    should write.
     * @return compressor stream
     * @throws IOException When the stream cannot be decorated.
     */
    public OutputStream decorateOutputStream(final OutputStream destination)
            throws IOException {
        return destination;
    }

    /**
     * Wraps the source stream with an optional decompression layer using
     * the selected algorithm.
     *
     * @param source The source stream where the decompressor stream should
     *               read from.
     * @return decompressor stream
     * @throws IOException When the stream cannot be decorated.
     */
    public InputStream decorateInputStream(final InputStream source)
            throws IOException {
        return source;
    }
}
