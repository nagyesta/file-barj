package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import lombok.NonNull;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The base implementation of the {@link FileMetadataChangeDetector}.
 *
 * @param <T> The type of the primary content evaluation criteria
 */
public abstract class BaseFileMetadataChangeDetector<T> implements FileMetadataChangeDetector {

    private final FileMetadataSetRepository repository;
    private final SortedMap<String, FileMetadataSetId> filesFromManifests;
    private final PermissionComparisonStrategy permissionComparisonStrategy;

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param repository         The repository we can use to access the metadata
     * @param filesFromManifests The files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected BaseFileMetadataChangeDetector(
            final @NotNull FileMetadataSetRepository repository,
            final @NotNull Map<String, FileMetadataSetId> filesFromManifests,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        this.repository = repository;
        this.filesFromManifests = new TreeMap<>(filesFromManifests);
        this.permissionComparisonStrategy = Objects.requireNonNullElse(permissionStrategy, PermissionComparisonStrategy.STRICT);
    }

    @Override
    public boolean hasMetadataChanged(
            final @NonNull FileMetadata previousMetadata,
            final @NonNull FileMetadata currentMetadata) {
        final var permissionsChanged = !permissionComparisonStrategy.matches(previousMetadata, currentMetadata);
        final var hiddenStatusChanged = !Objects.equals(currentMetadata.getHidden(), previousMetadata.getHidden());
        final var timesChanged = currentMetadata.getFileType() != FileType.SYMBOLIC_LINK
                && !Objects.equals(currentMetadata.getLastModifiedUtcEpochSeconds(), previousMetadata.getLastModifiedUtcEpochSeconds());
        return hiddenStatusChanged || permissionsChanged || timesChanged;
    }

    @Override
    public boolean isFromLastIncrement(
            final @NonNull FileMetadata fileMetadata) {
        return repository.containsFileId(filesFromManifests.get(filesFromManifests.lastKey()), fileMetadata.getId());
    }

    @Override
    public @Nullable FileMetadata findMostRelevantPreviousVersion(
            final @NonNull FileMetadata currentMetadata) {
        final var increments = filesFromManifests.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        final var previousSamePath = findPreviousVersionByAbsolutePath(currentMetadata.getAbsolutePath());
        if (previousSamePath != null && !hasContentChanged(previousSamePath, currentMetadata)) {
            return previousSamePath;
        }
        for (final var increment : increments) {
            final var index = filesFromManifests.get(increment);
            final var key = getPrimaryContentCriteria(currentMetadata);
            final var files = findFilesByPrimaryContentCriteria().apply(repository, index, key);
            if (!files.isEmpty()) {
                final var byPath = new TreeMap<BackupPath, FileMetadata>();
                files.stream()
                        .filter(metadata -> !hasContentChanged(metadata, currentMetadata))
                        .forEach(metadata -> byPath.put(metadata.getAbsolutePath(), metadata));
                if (!byPath.isEmpty()) {
                    return byPath.getOrDefault(currentMetadata.getAbsolutePath(), byPath.firstEntry().getValue());
                }
            }
        }
        return previousSamePath;
    }

    @Override
    public @Nullable FileMetadata findPreviousVersionByAbsolutePath(
            final @NonNull BackupPath absolutePath) {
        return filesFromManifests.keySet()
                .stream().sorted(Comparator.reverseOrder())
                .map(increment -> {
                    final var fileMetadataSetId = filesFromManifests.get(increment);
                    return repository.findFileByPath(fileMetadataSetId, absolutePath)
                            .filter(metadata -> metadata.getStatus() != Change.DELETED);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(null);
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
     * Returns a function that can find the files from the repository by matching based on the primary content criteria.
     *
     * @return filter function
     */
    protected abstract
    TriFunction<FileMetadataSetRepository, FileMetadataSetId, T, Set<FileMetadata>> findFilesByPrimaryContentCriteria();
}
