package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InMemoryFileMetadataSetRepository
        extends InMemoryBaseFileSetRepository<FileMetadataSetId, FileMetadata>
        implements FileMetadataSetRepository {

    private static final int BATCH_SIZE = 2500;

    private final Map<UUID, Map<BackupPath, FileMetadata>> metadataByFileSetAndPath = new ConcurrentHashMap<>();

    @Override
    protected FileMetadataSetId createFileSetId(final Consumer<FileMetadataSetId> closeWith) {
        return new FileMetadataSetId(closeWith);
    }

    @Override
    public void appendTo(
            @NonNull final FileMetadataSetId id,
            @NonNull final Collection<FileMetadata> values) {
        super.appendTo(id, values);
        final var map = metadataByFileSetAndPath.computeIfAbsent(id.id(), k -> new ConcurrentHashMap<>());
        values.forEach(metadata -> map.put(metadata.getAbsolutePath(), metadata));
    }

    @Override
    public void removeFileSet(@NonNull final FileMetadataSetId id) {
        super.removeFileSet(id);
        metadataByFileSetAndPath.remove(id.id());
    }

    @Override
    public Optional<FileMetadata> takeFirst(@NonNull final FileMetadataSetId id) {
        final var metadata = super.takeFirst(id);
        metadata.map(FileMetadata::getAbsolutePath)
                .ifPresent(metadataByFileSetAndPath.get(id.id())::remove);
        return metadata;
    }

    @Override
    public void forEachByChangeStatusesAndFileTypes(
            @NonNull final FileMetadataSetId id,
            @NonNull final Set<Change> changeStatuses,
            @NonNull final Set<FileType> fileTypes,
            @NonNull final ForkJoinPool threadPool,
            @NonNull final SortOrder order,
            @NonNull final Consumer<FileMetadata> consumer) {
        forEach(id, threadPool, order, metadata -> {
            if (changeStatuses.contains(metadata.getStatus())
                    && fileTypes.contains(metadata.getFileType())) {
                consumer.accept(metadata);
            }
        });
    }

    @Override
    public void forEachDuplicateOf(
            @NonNull final FileMetadataSetId id,
            @NonNull final Set<Change> changeStatuses,
            @NonNull final Set<FileType> fileTypes,
            @NonNull final DuplicateHandlingStrategy strategy,
            @NonNull final HashAlgorithm hashAlgorithm,
            @NonNull final ForkJoinPool threadPool,
            @NonNull final Consumer<List<List<FileMetadata>>> consumer) {
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
        var size = 0;
        for (final var group : groupedScope) {
            batch.add(group);
            size += group.size();
            if (size >= BATCH_SIZE) {
                partitionedScope.add(batch);
                batch = new ArrayList<>();
                size = 0;
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
}
