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

    /**
     * Creates a new instance with the previous manifests.
     *
     * @param filesFromManifests The files found in the previous manifests
     */
    protected BaseFileMetadataChangeDetector(
            @NotNull final Map<String, Map<UUID, FileMetadata>> filesFromManifests) {
        this.filesFromManifests = new TreeMap<>(filesFromManifests);
        final SortedMap<String, Map<T, List<FileMetadata>>> contentIndex = new TreeMap<>();
        final Map<String, FileMetadata> nameIndex = new TreeMap<>();
        index(this.filesFromManifests, contentIndex, nameIndex);
        this.contentIndex = contentIndex;
        this.nameIndex = nameIndex;
    }

    @Override
    public boolean hasMetadataChanged(
            @NonNull final FileMetadata previousMetadata,
            @NonNull final FileMetadata currentMetadata) {
        final var permissionsChanged = !Objects.equals(currentMetadata.getOwner(), previousMetadata.getOwner())
                || !Objects.equals(currentMetadata.getGroup(), previousMetadata.getGroup())
                || !Objects.equals(currentMetadata.getPosixPermissions(), previousMetadata.getPosixPermissions());
        final var hiddenStatusChanged = currentMetadata.getHidden() != previousMetadata.getHidden();
        final var timesChanged = currentMetadata.getFileType() != FileType.SYMBOLIC_LINK
                && (!Objects.equals(currentMetadata.getCreatedUtcEpochSeconds(), previousMetadata.getCreatedUtcEpochSeconds())
                || !Objects.equals(currentMetadata.getLastModifiedUtcEpochSeconds(), previousMetadata.getLastModifiedUtcEpochSeconds()));
        return hiddenStatusChanged || permissionsChanged || timesChanged;
    }

    @Override
    public boolean isFromLastIncrement(
            @NonNull final FileMetadata fileMetadata) {
        return filesFromManifests.get(filesFromManifests.lastKey()).containsKey(fileMetadata.getId());
    }

    @SuppressWarnings("checkstyle:TodoComment")
    @Nullable
    @Override
    public FileMetadata findMostRelevantPreviousVersion(
            @NonNull final FileMetadata currentMetadata) {
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

    @Nullable
    @Override
    public FileMetadata findPreviousVersionByAbsolutePath(
            @NonNull final BackupPath absolutePath) {
        return nameIndex.get(absolutePath.toString());
    }

    @NotNull
    @Override
    public Change classifyChange(
            @NonNull final FileMetadata previousMetadata,
            @NonNull final FileMetadata currentMetadata) {
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
            @NotNull final SortedMap<String, Map<UUID, FileMetadata>> filesFromManifests,
            @NotNull final SortedMap<String, Map<T, List<FileMetadata>>> contentIndexMap,
            @NotNull final Map<String, FileMetadata> nameIndexMap) {
        filesFromManifests.forEach((increment, files) -> {
            files.forEach((uuid, metadata) -> contentIndexMap.computeIfAbsent(increment, k -> new HashMap<>())
                    .computeIfAbsent(getPrimaryContentCriteria(metadata), k -> new ArrayList<>())
                    .add(metadata));
        });
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
