package com.github.nagyesta.filebarj.core.inspect.worker;

import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream;
import lombok.NonNull;

import java.time.Instant;
import java.util.Optional;

/**
 * Converter extracting summary information from a backup manifest.
 */
public class ManifestToSummaryConverter {

    /**
     * Converts a {@link BackupIncrementManifest} to a summary string.
     *
     * @param manifest the manifest
     * @return the summary
     */
    public String convertToSummaryString(final @NonNull BackupIncrementManifest manifest) {
        final var epochSeconds = manifest.getStartTimeUtcEpochSeconds();
        final var totalSize = manifest.getFiles().values().stream()
                .mapToLong(FileMetadata::getOriginalSizeBytes).sum() / ChunkingOutputStream.MEBIBYTE;
        return manifest.getBackupType().name() + " backup: " + manifest.getFileNamePrefix() + "\n"
                + "\tStarted at : " + Instant.ofEpochSecond(epochSeconds) + " (Epoch seconds: " + epochSeconds + ")\n"
                + "\tContains " + manifest.getFiles().size() + " files (" + totalSize + " MiB)\n"
                + "\tVersions   : " + manifest.getVersions() + "\n"
                + "\tEncrypted  : " + Optional.ofNullable(manifest.getConfiguration().getEncryptionKey()).isPresent() + "\n"
                + "\tHash alg.  : " + manifest.getConfiguration().getHashAlgorithm().name() + "\n"
                + "\tCompression: " + manifest.getConfiguration().getCompression().name();
    }
}
