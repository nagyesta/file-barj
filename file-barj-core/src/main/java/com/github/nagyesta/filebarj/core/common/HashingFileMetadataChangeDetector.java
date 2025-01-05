package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.ManifestId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.function.BiFunction;

/**
 * Hashing based implementation of the {@link FileMetadataChangeDetector}.
 */
public class HashingFileMetadataChangeDetector extends BaseFileMetadataChangeDetector<String> {

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param manifestDatabase   The database containing all files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected HashingFileMetadataChangeDetector(
            final @NotNull ManifestDatabase manifestDatabase,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        super(manifestDatabase, permissionStrategy);
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

    @Override
    protected BiFunction<ManifestDatabase, FileMetadata, SortedMap<ManifestId, List<FileMetadata>>> retrieveByPrimaryContentCriteria() {
        return (manifestDatabase, fileMetadata) -> manifestDatabase
                .retrieveFileMetadataByOriginalHash(fileMetadata.getOriginalHash());
    }
}
