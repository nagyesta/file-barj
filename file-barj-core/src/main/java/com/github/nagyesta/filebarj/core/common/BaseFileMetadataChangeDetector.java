package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
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

    private final SortedMap<String, Map<UUID, FileMetadata>> filesFromManifests;
    private final SortedMap<String, Map<T, List<FileMetadata>>> contentIndex;
    private final Map<String, FileMetadata> nameIndex;
    private final PermissionComparisonStrategy permissionComparisonStrategy;

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param filesFromManifests The files found in the previous manifests
     * @param permissionStrategy The permission comparison strategy
     */
    protected BaseFileMetadataChangeDetector(
            final @NotNull Map<String, Map<UUID, FileMetadata>> filesFromManifests,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        this.filesFromManifests = new TreeMap<>(filesFromManifests);
        final SortedMap<String, Map<T, List<FileMetadata>>> contentIndexSet = new TreeMap<>();
        final Map<String, FileMetadata> nameIndexMap = new TreeMap<>();
        index(this.filesFromManifests, contentIndexSet, nameIndexMap);
        this.contentIndex = contentIndexSet;
        this.nameIndex = nameIndexMap;
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
        return filesFromManifests.get(filesFromManifests.lastKey()).containsKey(fileMetadata.getId());
    }

    @Override
    public @Nullable FileMetadata findMostRelevantPreviousVersion(
            final @NonNull FileMetadata currentMetadata) {
        final var increments = filesFromManifests.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        final var previousSamePath = nameIndex.getOrDefault(currentMetadata.getAbsolutePath().toString(), null);
        if (previousSamePath != null && !hasContentChanged(previousSamePath, currentMetadata)) {
            return previousSamePath;
        }
        for (final var increment : increments) {
            final var index = contentIndex.get(increment);
            final var key = getPrimaryContentCriteria(currentMetadata);
            if (index.containsKey(key)) {
                final var byPath = new TreeMap<BackupPath, FileMetadata>();
                index.get(key).stream()
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
        return nameIndex.get(absolutePath.toString());
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

    private void index(
            final @NotNull SortedMap<String, Map<UUID, FileMetadata>> filesFromManifests,
            final @NotNull SortedMap<String, Map<T, List<FileMetadata>>> contentIndexMap,
            final @NotNull Map<String, FileMetadata> nameIndexMap) {
        filesFromManifests.forEach((increment, files) -> files
                .forEach((uuid, metadata) -> contentIndexMap
                        .computeIfAbsent(increment, k -> new HashMap<>())
                        .computeIfAbsent(getPrimaryContentCriteria(metadata), k -> new ArrayList<>())
                        .add(metadata)));
        //populate files in reverse manifest order to ensure each file has the latest metadata saved
        filesFromManifests.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .map(filesFromManifests::get)
                .forEachOrdered(files -> files.entrySet().stream()
                        .filter(entry -> entry.getValue().getStatus() != Change.DELETED)
                        //put the file only if it is not already in the index
                        .forEach(entry -> nameIndexMap.putIfAbsent(entry.getValue().getAbsolutePath().toString(), entry.getValue())));
    }
}
