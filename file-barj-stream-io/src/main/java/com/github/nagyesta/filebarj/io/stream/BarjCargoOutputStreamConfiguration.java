package com.github.nagyesta.filebarj.io.stream;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * The configuration for the BaRJ cargo streaming archival operations.
 */
@Data
@Builder
public class BarjCargoOutputStreamConfiguration {
    private static final int MEBIBYTES_IN_100_GIBIBYTES = 100 * 1024;
    /**
     * The folder where the archive's parts should be stored.
     */
    private final @NonNull Path folder;
    /**
     * The prefix of the archive's parts.
     */
    private final @NonNull String prefix;
    /**
     * The function used to compress the archived data.
     */
    private final @NonNull IoFunction<OutputStream, OutputStream> compressionFunction;
    /**
     * The algorithm used to hash the entries.
     */
    private final String hashAlgorithm;
    /**
     * The maximum file (chunk) size in mebibyte.
     */
    @Builder.Default
    private final int maxFileSizeMebibyte = MEBIBYTES_IN_100_GIBIBYTES;
    /**
     * The encryption key that should be used to encrypt the index file.
     */
    private final SecretKey indexEncryptionKey;
}
