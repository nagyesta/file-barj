package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InMemoryFileMetadataSetRepository
        extends InMemoryBaseFileSetRepository<FileMetadataSetId, FileMetadata>
        implements FileMetadataSetRepository {

    private static final int BATCH_SIZE = 250;

    private final Map<UUID, Map<BackupPath, FileMetadata>> metadataByFileSetAndPath = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, FileMetadata>> metadataByFileSetAndFileId = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Set<FileMetadata>>> metadataByFileSetAndOriginalFileSize = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Set<FileMetadata>>> metadataByFileSetAndOriginalHash = new ConcurrentHashMap<>();

    @Override
    protected FileMetadataSetId createFileSetId(final Consumer<FileMetadataSetId> closeWith) {
        return new FileMetadataSetId(closeWith);
    }

    @Override
    public void appendTo(
            @NotNull final FileMetadataSetId id,
            @NotNull final Collection<FileMetadata> values) {
        super.appendTo(id, values);
        final var mapByPath = metadataByFileSetAndPath.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.forEach(metadata -> mapByPath.put(metadata.getAbsolutePath(), metadata));
        final var mapByFileId = metadataByFileSetAndFileId.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.forEach(metadata -> mapByFileId.put(metadata.getId(), metadata));
        final var mapByOriginalFileSize = metadataByFileSetAndOriginalFileSize.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.stream()
                .filter(metadata -> metadata.getOriginalSizeBytes() != null)
                .forEach(metadata -> mapByOriginalFileSize.computeIfAbsent(metadata.getOriginalSizeBytes(),
                        k -> new ConcurrentSkipListSet<>()).add(metadata));
        final var mapByOriginalHash = metadataByFileSetAndOriginalHash.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.stream()
                .filter(metadata -> metadata.getOriginalHash() != null)
                .forEach(metadata -> mapByOriginalHash.computeIfAbsent(metadata.getOriginalHash(),
                        k -> new ConcurrentSkipListSet<>()).add(metadata));
    }

    @Override
    public void removeFileSet(@NotNull final FileMetadataSetId id) {
        super.removeFileSet(id);
        metadataByFileSetAndPath.remove(id.id());
        metadataByFileSetAndFileId.remove(id.id());
        metadataByFileSetAndOriginalFileSize.remove(id.id());
        metadataByFileSetAndOriginalHash.remove(id.id());
    }

    @Override
    public void close() {
        super.close();
        metadataByFileSetAndPath.clear();
        metadataByFileSetAndFileId.clear();
        metadataByFileSetAndOriginalFileSize.clear();
        metadataByFileSetAndOriginalHash.clear();
    }

    @Override
    public long countByType(
            @NotNull final FileMetadataSetId id,
            @NotNull final Collection<FileType> types) {
        return getFileSetById(id)
                .stream()
                .map(FileMetadata::getFileType)
                .filter(types::contains)
                .count();
    }

    @Override
    public SortedMap<FileType, Long> countsByType(final @NotNull FileMetadataSetId id) {
        return new TreeMap<>(getFileSetById(id).stream()
                .collect(Collectors.groupingBy(FileMetadata::getFileType, Collectors.counting())));
    }

    @Override
    public SortedMap<Change, Long> countsByStatus(final @NotNull FileMetadataSetId id) {
        return new TreeMap<>(getFileSetById(id).stream()
                .collect(Collectors.groupingBy(FileMetadata::getStatus, Collectors.counting())));
    }

    @Override
    public void forEachByChangeStatusesAndFileTypes(
            @NotNull final FileMetadataSetId id,
            @NotNull final Set<Change> changeStatuses,
            @NotNull final Set<FileType> fileTypes,
            @NotNull final ForkJoinPool threadPool,
            @NotNull final SortOrder order,
            @NotNull final Consumer<FileMetadata> consumer) {
        forEach(id, threadPool, order, metadata -> {
            if (changeStatuses.contains(metadata.getStatus())
                    && fileTypes.contains(metadata.getFileType())) {
                consumer.accept(metadata);
            }
        });
    }

    @Override
    public void forEachDuplicateOf(
            @NotNull final FileMetadataSetId id,
            @NotNull final Set<Change> changeStatuses,
            @NotNull final Set<FileType> fileTypes,
            @NotNull final DuplicateHandlingStrategy strategy,
            @NotNull final HashAlgorithm hashAlgorithm,
            @NotNull final ForkJoinPool threadPool,
            @NotNull final Consumer<List<List<FileMetadata>>> consumer) {
        final var groupedDuplicates = partition(getFileSetById(id).stream()
                .filter(metadata -> changeStatuses.contains(metadata.getStatus()))
                .filter(metadata -> fileTypes.contains(metadata.getFileType()))
                .collect(Collectors.groupingBy(strategy.fileGroupingFunctionForHash(hashAlgorithm)))
                .values());
        threadPool.submit(() -> groupedDuplicates.stream().parallel().forEach(consumer)).join();
    }

    private @NotNull List<List<List<FileMetadata>>> partition(final @NotNull Collection<List<FileMetadata>> groupedScope) {
        final List<List<List<FileMetadata>>> partitionedScope = new ArrayList<>();
        var batch = new ArrayList<List<FileMetadata>>();
        for (final var group : groupedScope) {
            batch.add(group);
            if (batch.size() == BATCH_SIZE) {
                partitionedScope.add(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            partitionedScope.add(batch);
        }
        return partitionedScope;
    }

    @Override
    public long getOriginalSizeBytes(final FileMetadataSetId id) {
        return getFileSetById(id)
                .stream()
                .mapToLong(FileMetadata::getOriginalSizeBytes)
                .sum();
    }

    @Override
    public boolean containsFileId(
            @NotNull final FileMetadataSetId id,
            @NotNull final UUID fileId) {
        final var map = metadataByFileSetAndFileId.get(id.id());
        if (map == null) {
            return false;
        }
        return map.containsKey(fileId);
    }

    @Override
    public Set<FileMetadata> findFilesByOriginalHash(
            @NotNull final FileMetadataSetId id,
            @NotNull final String originalHash) {
        final var map = metadataByFileSetAndOriginalHash.get(id.id());
        if (map == null || !map.containsKey(originalHash)) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(map.get(originalHash));
    }

    @Override
    public Set<FileMetadata> findFilesByOriginalSize(
            @NotNull final FileMetadataSetId id,
            @NotNull final Long originalSize) {
        final var map = metadataByFileSetAndOriginalFileSize.get(id.id());
        if (map == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(map.get(originalSize));
    }

    @Override
    public Optional<FileMetadata> findFileByPath(
            @NotNull final FileMetadataSetId id,
            @NotNull final BackupPath absolutePath) {
        return Optional.ofNullable(metadataByFileSetAndPath.get(id.id()))
                .map(map -> map.get(absolutePath));
    }

    @Override
    public List<FileMetadata> findErrorsOf(@NotNull final FileMetadataSetId id) {
        return getFileSetById(id)
                .stream()
                .filter(fileMetadata -> fileMetadata.getError() != null)
                .toList();
    }

    @Override
    public void updateArchiveMetadataId(
            @NotNull final FileMetadataSetId id,
            @NotNull final UUID metadataId,
            @Nullable final UUID archiveMetadataId) {
        final var map = metadataByFileSetAndFileId.get(id.id());
        if (map == null || !map.containsKey(metadataId)) {
            return;
        }
        map.get(metadataId).setArchiveMetadataId(archiveMetadataId);
    }

    @Override
    public FileMetadataSetId intersectByPath(
            @NotNull final FileMetadataSetId a,
            @NotNull final FileMetadataSetId b) {
        final var result = createFileSet();
        final var mapA = metadataByFileSetAndPath.get(a.id());
        final var mapB = metadataByFileSetAndPath.get(b.id());
        if (mapA == null || mapB == null) {
            return result;
        }
        mapA.keySet()
                .stream()
                .filter(mapB::containsKey)
                .forEach(key -> appendTo(result, mapA.get(key)));
        return result;
    }

    @Override
    public FileMetadataSetId keepChangedContent(
            @NotNull final FileMetadataSetId id,
            @NotNull final BackupPathChangeStatusMapId changeStats) {
        final var backupPathChangeStatusMapRepository = dataStore().backupPathChangeStatusMapRepository();
        final var result = createFileSet();
        getFileSetById(id).stream()
                .filter(file -> file.getFileType().isContentSource())
                //keep only content sources that have changed
                //if the change status is missing, that means the file was put out of scope earlier
                .filter(file -> backupPathChangeStatusMapRepository
                        .getOrDefault(changeStats, file.getAbsolutePath(), Change.NO_CHANGE)
                        .isRestoreContent())
                .forEach(file -> appendTo(result, file));
        return result;
    }

    @Override
    public FileMetadataSetId keepChangedMetadata(
            @NotNull final FileMetadataSetId id,
            @NotNull final Set<FileType> fileTypes,
            @NotNull final BackupPathChangeStatusMapId changeStats) {
        final var backupPathChangeStatusMapRepository = dataStore().backupPathChangeStatusMapRepository();
        final var result = createFileSet();
        getFileSetById(id).stream()
                .filter(file -> fileTypes.contains(file.getFileType()))
                //if the change status is missing, that means the file was put out of scope earlier
                .filter(file -> backupPathChangeStatusMapRepository
                        .getOrDefault(changeStats, file.getAbsolutePath(), Change.NO_CHANGE)
                        .isRestoreMetadata())
                .forEach(file -> appendTo(result, file));
        return result;
    }

    @Override
    public SortedSet<FileMetadata> findFilesByIds(
            @NotNull final FileMetadataSetId id,
            @NotNull final Set<UUID> files) {
        final var fileMetadataMap = metadataByFileSetAndFileId.get(id.id());
        if (fileMetadataMap == null) {
            return Collections.emptySortedSet();
        }
        return files.stream()
                .filter(fileMetadataMap::containsKey)
                .map(fileMetadataMap::get)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public boolean containsPath(
            @NotNull final FileMetadataSetId id,
            @NotNull final String absolutePath) {
        final var map = metadataByFileSetAndPath.get(id.id());
        return map != null && map.keySet()
                .stream()
                .anyMatch(path -> path.toString().equals(absolutePath));
    }
}
