package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Hashing based implementation of the {@link FileMetadataChangeDetector}.
 */
public class HashingFileMetadataChangeDetector extends BaseFileMetadataChangeDetector<String> {

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param filesFromManifests The files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected HashingFileMetadataChangeDetector(
            final @NotNull Map<String, Map<UUID, FileMetadata>> filesFromManifests,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        super(filesFromManifests, permissionStrategy);
    }

    @Override
    public boolean hasContentChanged(
            final @NonNull FileMetadata previousMetadata,
            final @NonNull FileMetadata currentMetadata) {
        final var isContentSource = previousMetadata.getFileType().isContentSource() || currentMetadata.getFileType().isContentSource();
        final var hasContentChanged = !Objects.equals(previousMetadata.getFileType(), currentMetadata.getFileType())
                || !Objects.equals(previousMetadata.getOriginalHash(), currentMetadata.getOriginalHash())
                || !Objects.equals(previousMetadata.getOriginalSizeBytes(), currentMetadata.getOriginalSizeBytes());
        return isContentSource && hasContentChanged;
    }

    @Override
    protected String getPrimaryContentCriteria(final @NotNull FileMetadata metadata) {
        return metadata.getOriginalHash();
    }
}
