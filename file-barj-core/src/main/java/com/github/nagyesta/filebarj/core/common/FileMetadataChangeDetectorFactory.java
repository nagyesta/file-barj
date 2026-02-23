package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Factory for {@link FileMetadataChangeDetector} instances.
 */
public final class FileMetadataChangeDetectorFactory {

    private FileMetadataChangeDetectorFactory() {
    }

    /**
     * Creates a new instance with the suitable instance type based on the previous manifests.
     *
     * @param configuration             The backup configuration
     * @param fileMetadataSetRepository The repository storing the file metadata
     * @param filesFromManifests        The previous manifests
     * @param permissionStrategy        The permission comparison strategy
     * @return The new instance
     */
    public static FileMetadataChangeDetector create(
            final @NonNull BackupJobConfiguration configuration,
            final @NonNull FileMetadataSetRepository fileMetadataSetRepository,
            final @NonNull Map<String, FileMetadataSetId> filesFromManifests,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        if (filesFromManifests.isEmpty()) {
            throw new IllegalArgumentException("Previous manifests cannot be empty");
        }
        if (configuration.getHashAlgorithm() == HashAlgorithm.NONE) {
            return new SimpleFileMetadataChangeDetector(fileMetadataSetRepository, filesFromManifests, permissionStrategy);
        } else {
            return new HashingFileMetadataChangeDetector(fileMetadataSetRepository, filesFromManifests, permissionStrategy);
        }
    }
}
