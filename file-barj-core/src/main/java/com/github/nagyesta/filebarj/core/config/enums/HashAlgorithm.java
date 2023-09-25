package com.github.nagyesta.filebarj.core.config.enums;

import lombok.Getter;
import lombok.ToString;

/**
 * Defines the supported hash algorithms used for checksum calculations.
 */
@Getter
@ToString
public enum HashAlgorithm {
    /**
     * No checksum calculation needed.
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
}
