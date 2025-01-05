package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.ManifestId;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

/**
 * The base implementation of the {@link FileMetadataChangeDetector}.
 *
 * @param <T> The type of the primary content evaluation criteria
 */
public abstract class BaseFileMetadataChangeDetector<T> implements FileMetadataChangeDetector {

    private final ManifestDatabase manifestDatabase;
    private final PermissionComparisonStrategy permissionComparisonStrategy;

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param manifestDatabase   The database containing all files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected BaseFileMetadataChangeDetector(
            final @NonNull ManifestDatabase manifestDatabase,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        this.manifestDatabase = manifestDatabase;
        this.permissionComparisonStrategy = Objects.requireNonNullElse(permissionStrategy, PermissionComparisonStrategy.STRICT);
    }

    @Override
    public boolean hasMetadataChanged(
            final @NonNull FileMetadata previousMetadata,
            final @NonNull FileMetadata currentMetadata) {
        final var permissionsChanged = !permissionComparisonStrategy.matches(previousMetadata, currentMetadata);
        final var hiddenStatusChanged = currentMetadata.getHidden() != previousMetadata.getHidden();
        final var timesChanged = currentMetadata.getFileType() != FileType.SYMBOLIC_LINK
                && !Objects.equals(currentMetadata.getLastModifiedUtcEpochSeconds(), previousMetadata.getLastModifiedUtcEpochSeconds());
        return hiddenStatusChanged || permissionsChanged || timesChanged;
    }

    @Override
    public boolean isFromLastIncrement(
            final @NonNull FileMetadata fileMetadata) {
        return manifestDatabase.existsInLastIncrement(fileMetadata);
    }

    @Override
    public @Nullable FileMetadata findMostRelevantPreviousVersion(
            final @NonNull FileMetadata currentMetadata) {
        final var matchingFiles = retrieveByPrimaryContentCriteria().apply(manifestDatabase, currentMetadata);
        final var increments = matchingFiles.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        final var previousSamePath = manifestDatabase.retrieveLatestFileMetadataBySourcePath(currentMetadata.getAbsolutePath());
        if (previousSamePath != null && !hasContentChanged(previousSamePath, currentMetadata)) {
            return previousSamePath;
        }
        for (final var increment : increments) {
            final var index = matchingFiles.get(increment);
            final var byPath = new TreeMap<BackupPath, FileMetadata>();
            index.stream()
                    .filter(metadata -> !hasContentChanged(metadata, currentMetadata))
                    .forEach(metadata -> byPath.put(metadata.getAbsolutePath(), metadata));
            if (!byPath.isEmpty()) {
                return byPath.getOrDefault(currentMetadata.getAbsolutePath(), byPath.firstEntry().getValue());
            }
        }
        return previousSamePath;
    }

    @Override
    public @Nullable FileMetadata findPreviousVersionByAbsolutePath(
            final @NonNull BackupPath absolutePath) {
        return manifestDatabase.retrieveLatestFileMetadataBySourcePath(absolutePath);
    }

    @Override
    public @NotNull Change classifyChange(
            final @NonNull FileMetadata previousMetadata,
            final @NonNull FileMetadata currentMetadata) {
        if (currentMetadata.getFileType() == FileType.MISSING) {
            return Change.DELETED;
        } else if (previousMetadata.getFileType() == FileType.MISSING) {
            return Change.NEW;
        } else if (hasContentChanged(previousMetadata, currentMetadata)) {
            return Change.CONTENT_CHANGED;
        } else if (!isFromLastIncrement(previousMetadata)) {
            return Change.ROLLED_BACK;
        } else if (hasMetadataChanged(previousMetadata, currentMetadata)) {
            return Change.METADATA_CHANGED;
        } else {
            return Change.NO_CHANGE;
        }
    }

    /**
     * Returns the value of the primary criteria used for content comparison.
     *
     * @param metadata the metadata
     * @return the value
     */
    protected abstract T getPrimaryContentCriteria(@NotNull FileMetadata metadata);

    /**
     * Returns the function that can obtain the matching files based on the primary content criteria.
     *
     * @return bi-function
     */
    @SuppressWarnings("checkstyle:LineLength")
    protected abstract BiFunction<ManifestDatabase, FileMetadata, SortedMap<ManifestId, List<FileMetadata>>> retrieveByPrimaryContentCriteria();

}
