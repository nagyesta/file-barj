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
 * Size based implementation of the {@link FileMetadataChangeDetector}.
 */
public class SimpleFileMetadataChangeDetector extends BaseFileMetadataChangeDetector<Long> {

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param manifestDatabase   The database containing all files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected SimpleFileMetadataChangeDetector(
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
                || !Objects.equals(previousMetadata.getOriginalSizeBytes(), currentMetadata.getOriginalSizeBytes())
                || !Objects.equals(previousMetadata.getLastModifiedUtcEpochSeconds(), currentMetadata.getLastModifiedUtcEpochSeconds())
                || !Objects.equals(getFileName(previousMetadata), getFileName(currentMetadata));
        return isContentSource && hasContentChanged;
    }

    @Override
    protected Long getPrimaryContentCriteria(final @NotNull FileMetadata metadata) {
        return metadata.getOriginalSizeBytes();
    }

    private static @NotNull String getFileName(final @NotNull FileMetadata fileMetadata) {
        return fileMetadata.getAbsolutePath().getFileName();
    }

    @Override
    protected BiFunction<ManifestDatabase, FileMetadata, SortedMap<ManifestId, List<FileMetadata>>> retrieveByPrimaryContentCriteria() {
        return (manifestDatabase, fileMetadata) -> manifestDatabase
                .retrieveFileMetadataByOriginalSizeBytes(fileMetadata.getOriginalSizeBytes());
    }
}
