package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
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
     * @param configuration      The backup configuration
     * @param filesFromManifests The files found in the previous manifests
     */
    protected BaseFileMetadataChangeDetector(
            @NotNull final BackupJobConfiguration configuration,
            @NotNull final Map<String, Map<UUID, FileMetadata>> filesFromManifests) {
        this.filesFromManifests = new TreeMap<>(filesFromManifests);
        final SortedMap<String, Map<T, List<FileMetadata>>> contentIndex = new TreeMap<>();
        final Map<String, FileMetadata> nameIndex = new TreeMap<>();
        index(configuration, this.filesFromManifests, contentIndex, nameIndex);
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
        //TODO: will be needed later for the incremental backup
        final var increments = filesFromManifests.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        for (final var increment : increments) {
            final var index = contentIndex.get(increment);
            final var key = getPrimaryContentCriteria(currentMetadata);
            if (index.containsKey(key)) {
                for (final var metadata : index.get(key)) {
                    if (!hasContentChanged(metadata, currentMetadata)) {
                        return metadata;
                    }
                }
            }
        }
        if (nameIndex.containsKey(currentMetadata.getAbsolutePath().toString())) {
            return nameIndex.get(currentMetadata.getAbsolutePath().toString());
        }
        return null;
    }

    @Nullable
    @Override
    public FileMetadata findPreviousVersionByAbsolutePath(
            @NonNull final Path absolutePath) {
        return nameIndex.get(absolutePath.toString());
    }

    @NotNull
    @Override
    public Change classifyChange(
            @NonNull final FileMetadata previousMetadata,
            @NonNull final FileMetadata currentMetadata) {
        if (!Files.exists(currentMetadata.getAbsolutePath())) {
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
            @NotNull final BackupJobConfiguration configuration,
            @NotNull final SortedMap<String, Map<UUID, FileMetadata>> filesFromManifests,
            @NotNull final SortedMap<String, Map<T, List<FileMetadata>>> contentIndexMap,
            @NotNull final Map<String, FileMetadata> nameIndexMap) {
        filesFromManifests.forEach((increment, files) -> {
            final var ignoreHash = configuration.getHashAlgorithm().equals(HashAlgorithm.NONE);
            files.forEach((uuid, metadata) -> contentIndexMap.computeIfAbsent(increment, k -> new HashMap<>())
                    .computeIfAbsent(getPrimaryContentCriteria(metadata), k -> new ArrayList<>())
                    .add(metadata));
        });
        filesFromManifests.get(filesFromManifests.lastKey()).entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() != Change.DELETED)
                .forEach(entry -> nameIndexMap.put(entry.getValue().getAbsolutePath().toString(), entry.getValue()));
    }
}
