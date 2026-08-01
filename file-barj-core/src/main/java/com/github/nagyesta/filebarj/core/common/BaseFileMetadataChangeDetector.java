package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.FileMetadataIndex;
import lombok.NonNull;
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
    private final Map<BackupPath, FileMetadataIndex> indexByPath = new HashMap<>();
    private final Map<T, Set<FileMetadataIndex>> indexByPrimaryCriteria = new HashMap<>();

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
        final var previousSamePath = findPreviousIndexVersionByAbsolutePath(currentMetadata.getAbsolutePath());
        if (previousSamePath != null && !hasContentChanged(previousSamePath, currentMetadata)) {
            return getFileMetadata(previousSamePath);
        }
        final var key = getPrimaryContentCriteria(currentMetadata);
        final var files = indexByPrimaryCriteria.getOrDefault(key, Collections.emptySet());
        if (!files.isEmpty()) {
            final var byPath = new TreeMap<BackupPath, FileMetadataIndex>();
            files.stream()
                    .filter(metadata -> !hasContentChanged(metadata, currentMetadata))
                    .forEach(metadata -> byPath.put(metadata.absolutePath(), metadata));
            if (!byPath.isEmpty()) {
                final var index = byPath.getOrDefault(currentMetadata.getAbsolutePath(), byPath.firstEntry().getValue());
                return getFileMetadata(index);
            }
        }
        if (previousSamePath == null) {
            return null;
        }
        return getFileMetadata(previousSamePath);
    }

    @Override
    public @Nullable FileMetadata findPreviousVersionByAbsolutePath(
            final @NonNull BackupPath absolutePath) {
        final var index = findPreviousIndexVersionByAbsolutePath(absolutePath);
        if (index == null) {
            return null;
        }
        return getFileMetadata(index);
    }

    @Override
    public @Nullable FileMetadataIndex findPreviousIndexVersionByAbsolutePath(
            final @NonNull BackupPath absolutePath) {
        return indexByPath.getOrDefault(absolutePath, null);
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

    @Override
    public void index() {
        filesFromManifests.keySet()
                .stream()
                .sorted(Comparator.reverseOrder())
                .forEachOrdered(key -> {
                    final var setId = filesFromManifests.get(key);
                    repository.forEachForIndex(setId, item -> {
                        indexByPath.putIfAbsent(item.absolutePath(), item);
                        if (item.fileType().isContentSource()) {
                            final var value = getPrimaryContentCriteria(item);
                            indexByPrimaryCriteria.computeIfAbsent(value, c -> new LinkedHashSet<>())
                                    .add(item);
                        }
                    });
                });
    }

    @Override
    public void clearIndex() {
        indexByPath.clear();
        indexByPrimaryCriteria.clear();
    }

    /**
     * Returns the value of the primary criteria used for content comparison.
     *
     * @param metadata the metadata
     * @return the value
     */
    protected abstract T getPrimaryContentCriteria(@NotNull FileMetadata metadata);

    /**
     * Returns the value of the primary criteria used for content comparison.
     *
     * @param metadata the metadata
     * @return the value
     */
    protected abstract T getPrimaryContentCriteria(@NotNull FileMetadataIndex metadata);

    private FileMetadata getFileMetadata(final FileMetadataIndex index) {
        return repository.findFileById(index.fileSetId(), index.id())
                .orElseThrow(() -> new ArithmeticException("Failed to find file metadata based on index!"));
    }

}
