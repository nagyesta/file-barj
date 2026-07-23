package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.FileMetadataIndex;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Hashing based implementation of the {@link FileMetadataChangeDetector}.
 */
public class HashingFileMetadataChangeDetector extends BaseFileMetadataChangeDetector<String> {

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param repository         The repository we can use to access the metadata
     * @param filesFromManifests The files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected HashingFileMetadataChangeDetector(
            final @NotNull FileMetadataSetRepository repository,
            final @NotNull Map<String, FileMetadataSetId> filesFromManifests,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        super(repository, filesFromManifests, permissionStrategy);
    }

    @Override
    public boolean hasContentChanged(
            final @NonNull FileMetadata previousMetadata,
            final @NonNull FileMetadata currentMetadata) {
        final var isContentSource = previousMetadata.getFileType().isContentSource()
                || currentMetadata.getFileType().isContentSource();
        final var hasContentChanged = !Objects.equals(previousMetadata.getFileType(), currentMetadata.getFileType())
                || !Objects.equals(previousMetadata.getOriginalHash(), currentMetadata.getOriginalHash())
                || !Objects.equals(previousMetadata.getOriginalSizeBytes(), currentMetadata.getOriginalSizeBytes());
        return isContentSource && hasContentChanged;
    }

    @Override
    public boolean hasContentChanged(
            final @NonNull FileMetadataIndex previousMetadata,
            final @NonNull FileMetadata currentMetadata) {
        final var isContentSource = previousMetadata.fileType().isContentSource()
                || currentMetadata.getFileType().isContentSource();
        final var hasContentChanged = !Objects.equals(previousMetadata.fileType(), currentMetadata.getFileType())
                || !Objects.equals(previousMetadata.originalHash(), currentMetadata.getOriginalHash())
                || !Objects.equals(previousMetadata.originalSizeBytes(), currentMetadata.getOriginalSizeBytes());
        return isContentSource && hasContentChanged;
    }

    @Override
    protected String getPrimaryContentCriteria(final @NotNull FileMetadata metadata) {
        return metadata.getOriginalHash();
    }

    @Override
    protected String getPrimaryContentCriteria(final @NotNull FileMetadataIndex metadata) {
        return metadata.originalHash();
    }

}
