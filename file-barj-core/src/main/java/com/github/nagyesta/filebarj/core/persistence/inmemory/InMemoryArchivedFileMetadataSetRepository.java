package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.persistence.ArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InMemoryArchivedFileMetadataSetRepository
        extends InMemoryBaseFileSetRepository<ArchivedFileMetadataSetId, ArchivedFileMetadata>
        implements ArchivedFileMetadataSetRepository {

    private final Map<UUID, Map<UUID, ArchivedFileMetadata>> metadataByFileSetAndFileId = new ConcurrentHashMap<>();
    private final Map<UUID, Map<ArchiveEntryLocator, Set<ArchivedFileMetadata>>> metadataByFileSetAndLocator = new ConcurrentHashMap<>();

    @Override
    protected ArchivedFileMetadataSetId createFileSetId(final Consumer<ArchivedFileMetadataSetId> closeWith) {
        return new ArchivedFileMetadataSetId(closeWith);
    }

    @Override
    public void appendTo(
            @NotNull final ArchivedFileMetadataSetId id,
            @NotNull final Collection<ArchivedFileMetadata> values) {
        super.appendTo(id, values);
        final var mapByFileId = metadataByFileSetAndFileId.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.forEach(metadata -> mapByFileId.put(metadata.getId(), metadata));
        final var mapByLocator = metadataByFileSetAndLocator.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.forEach(metadata -> mapByLocator
                .computeIfAbsent(metadata.getArchiveLocation(), k -> new ConcurrentSkipListSet<>()).add(metadata));
    }

    @Override
    public void removeFileSet(@NotNull final ArchivedFileMetadataSetId id) {
        super.removeFileSet(id);
        metadataByFileSetAndFileId.remove(id.id());
        metadataByFileSetAndLocator.remove(id.id());
    }

    @Override
    public void close() {
        super.close();
        metadataByFileSetAndFileId.clear();
        metadataByFileSetAndLocator.clear();
    }

    @Override
    public long countAllFiles(@NotNull final ArchivedFileMetadataSetId id) {
        return getFileSetById(id)
                .stream()
                .map(ArchivedFileMetadata::getFiles)
                .mapToLong(Collection::size)
                .sum();
    }

    @Override
    public Set<UUID> containsFileMetadataIds(
            @NotNull final ArchivedFileMetadataSetId id,
            @NotNull final Collection<UUID> fileMetadataIds) {
        return getFileSetById(id)
                .stream()
                .map(ArchivedFileMetadata::getFiles)
                .flatMap(Collection::stream)
                .filter(fileMetadataIds::contains)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<UUID, ArchivedFileMetadata> findByFileMetadataIds(
            @NotNull final ArchivedFileMetadataSetId id,
            @NotNull final Collection<UUID> fileMetadataIds) {
        final var result = new ConcurrentHashMap<UUID, ArchivedFileMetadata>();
        getFileSetById(id).forEach(metadata -> fileMetadataIds.stream()
                .filter(metadata.getFiles()::contains)
                .forEach(fileMetadataId -> result.put(fileMetadataId, metadata)));
        return result;
    }

    @Override
    public ArchivedFileMetadataSetId intersectWithFileMetadata(
            @NotNull final ArchivedFileMetadataSetId id,
            @NotNull final FileMetadataSetId fileMetadataSetId) {
        final var result = createFileSet();
        final var archivedMetadataByFileId = metadataByFileSetAndFileId.get(id.id());
        dataStore().fileMetadataSetRepository()
                .forEach(fileMetadataSetId,
                        dataStore().singleThreadedPool(),
                        metadata -> {
                            if (metadata.getArchiveMetadataId() != null) {
                                appendTo(result, archivedMetadataByFileId.get(metadata.getArchiveMetadataId()));
                            }
                        });
        return result;
    }

    @Override
    public ArchivedFileMetadataSetId filterByBackupIncrements(
            @NotNull final ArchivedFileMetadataSetId id,
            @NotNull final SortedSet<Integer> versions) {
        final var result = createFileSet();
        final var archivedMetadataByLocator = metadataByFileSetAndLocator.get(id.id());
        if (archivedMetadataByLocator != null) {
            archivedMetadataByLocator.entrySet().stream()
                    .filter(entry -> versions.contains(entry.getKey().getBackupIncrement()))
                    .forEach(entry -> appendTo(result, entry.getValue()));
        }
        return result;
    }

    @Override
    public Set<String> asEntryPaths(@NotNull final ArchivedFileMetadataSetId id) {
        final var archivedMetadataByLocator = metadataByFileSetAndLocator.get(id.id());
        if (archivedMetadataByLocator == null) {
            return Set.of();
        }
        return archivedMetadataByLocator.keySet().stream()
                .map(ArchiveEntryLocator::asEntryPath)
                .collect(Collectors.toSet());
    }

    @Override
    public SortedSet<FileMetadata> findFileMetadataByArchiveLocator(
            @NotNull final ArchivedFileMetadataSetId id,
            @NotNull final FileMetadataSetId fileId,
            @NotNull final ArchiveEntryLocator currentLocator) {
        final var archivedMetadataByLocator = metadataByFileSetAndLocator.get(id.id());
        if (archivedMetadataByLocator == null || !archivedMetadataByLocator.containsKey(currentLocator)) {
            return Collections.emptySortedSet();
        }
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        return fileMetadataSetRepository.findFilesByIds(fileId, archivedMetadataByLocator.get(currentLocator)
                .stream()
                .map(ArchivedFileMetadata::getFiles)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()));
    }
}
