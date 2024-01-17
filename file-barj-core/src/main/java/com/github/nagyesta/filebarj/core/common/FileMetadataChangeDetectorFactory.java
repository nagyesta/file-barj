package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;

import java.util.Map;
import java.util.UUID;

/**
 * Factory for {@link FileMetadataChangeDetector} instances.
 */
public class FileMetadataChangeDetectorFactory {

    /**
     * Creates a new instance with the suitable instance type based on the previous manifests.
     *
     * @param configuration      The backup configuration
     * @param filesFromManifests The previous manifests
     * @return The new instance
     */
    public static FileMetadataChangeDetector create(
            @NonNull final BackupJobConfiguration configuration,
            @NonNull final Map<String, Map<UUID, FileMetadata>> filesFromManifests) {
        if (filesFromManifests.isEmpty()) {
            throw new IllegalArgumentException("Previous manifests cannot be empty");
        }
        if (configuration.getHashAlgorithm() == HashAlgorithm.NONE) {
            return new SimpleFileMetadataChangeDetector(filesFromManifests);
        } else {
            return new HashingFileMetadataChangeDetector(filesFromManifests);
        }
    }
}
