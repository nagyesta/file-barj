package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for {@link FileMetadataChangeDetector} instances.
 */
public class FileMetadataChangeDetectorFactory {

    /**
     * Creates a new instance with the suitable instance type based on the previous manifests.
     *
     * @param manifestDatabase   The database containing all files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     * @return The new instance
     */
    public static FileMetadataChangeDetector create(
            final @NonNull ManifestDatabase manifestDatabase,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        if (manifestDatabase.isEmpty()) {
            throw new IllegalArgumentException("Previous manifests cannot be empty");
        }
        if (manifestDatabase.getLatestConfiguration().getHashAlgorithm() == HashAlgorithm.NONE) {
            return new SimpleFileMetadataChangeDetector(manifestDatabase, permissionStrategy);
        } else {
            return new HashingFileMetadataChangeDetector(manifestDatabase, permissionStrategy);
        }
    }
}
