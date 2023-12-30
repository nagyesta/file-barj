package com.github.nagyesta.filebarj.core.config.enums;

import com.github.nagyesta.filebarj.io.stream.internal.OptionalDigestOutputStream;
import lombok.Getter;
import lombok.ToString;

import java.io.OutputStream;

/**
 * Defines the supported hash algorithms used for hash calculations.
 */
@Getter
@ToString
public enum HashAlgorithm {
    /**
     * No hash calculation needed.
     */
    NONE(null),
    /**
     * MD5.
     */
    MD5("MD5"),
    /**
     * SHA-1.
     */
    SHA1("SHA-1"),
    /**
     * SHA-256.
     */
    SHA256("SHA-256"),
    /**
     * SHA-512.
     */
    SHA512("SHA-512");

    private final String algorithmName;

    /**
     * Constructs an enum for the provided algorithm.
     *
     * @param algorithmName The algorithm.
     */
    HashAlgorithm(final String algorithmName) {
        this.algorithmName = algorithmName;
    }

    /**
     * Decorates the provided stream with an optional digest calculator using the algorithm
     * represented by this.
     *
     * @param stream The destination stream.
     * @return Decorated stream
     */
    public OptionalDigestOutputStream decorate(final OutputStream stream) {
        return new OptionalDigestOutputStream(stream, this.getAlgorithmName());
    }
}
