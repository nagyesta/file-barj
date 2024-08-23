package com.github.nagyesta.filebarj.io.stream;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * The configuration for the BaRJ cargo streaming unpacking operations.
 */
@Data
@Builder
public class BarjCargoInputStreamConfiguration {
    /**
     * The folder where the archive's parts are stored.
     */
    private final @NonNull Path folder;
    /**
     * The prefix of the archive's parts.
     */
    private final @NonNull String prefix;
    /**
     * The function used to compress the archived data.
     */
    private final @NonNull IoFunction<InputStream, InputStream> compressionFunction;
    /**
     * The algorithm used to hash the entries.
     */
    private final String hashAlgorithm;
    /**
     * The decryption key that should be used to decrypt the index file.
     */
    private final SecretKey indexDecryptionKey;
}
