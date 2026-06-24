package com.github.nagyesta.filebarj.core.persistence.h2;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.FileMetadataIndex;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.GroupedIdCollection;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2FileMetadataSetRepositoryExtension;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import static com.github.nagyesta.filebarj.core.persistence.DataStore.BATCH_CHUNK_SIZE;

public class H2FileMetadataSetRepository
        implements FileMetadataSetRepository {

    private final Jdbi jdbi;
    private final Set<FileMetadataSetId> openSets = new CopyOnWriteArraySet<>();
    private DataStore dataStore;

    public H2FileMetadataSetRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public FileMetadataSetId createFileSet() {
        return createFileSetId(this::removeFileSet);
    }

    @Override
    public void appendTo(
            final FileMetadataSetId id,
            final FileMetadata value) {
        appendTo(id, Collections.singletonList(value));
    }

    @Override
    public void appendTo(
            final FileMetadataSetId id,
            final Collection<FileMetadata> values) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.appendTo(id, values);
            return null;
        });
    }

    @Override
    public void removeFileSet(final FileMetadataSetId id) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.deleteAll(id);
            return null;
        });
    }

    public List<FileMetadata> findAll(final FileMetadataSetId id) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .fetchAsc(id)
                .collectIntoList()
        );
    }

    @Override
    public long countAll(final FileMetadataSetId id) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .countAll(id));
    }

    @Override
    public boolean isEmpty(final FileMetadataSetId id) {
        return !jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .exists(id));
    }

    @Override
    public void forEach(
            final FileMetadataSetId id,
            final ForkJoinPool threadPool,
            final Consumer<FileMetadata> consumer) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetch(id)
                    .stream()
                    .parallel()
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public void forEachAsc(
            final FileMetadataSetId id,
            final ForkJoinPool threadPool,
            final Consumer<FileMetadata> consumer) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetchAsc(id)
                    .stream()
                    .parallel()
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public long countByType(
            final FileMetadataSetId id,
            final Collection<FileType> types) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .countByType(id, Set.copyOf(types)));
    }

    @Override
    public SortedMap<FileType, Long> countsByType(final FileMetadataSetId id) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .countsByType(id));
    }

    @Override
    public SortedMap<Change, Long> countsByStatus(final FileMetadataSetId id) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .countsByStatus(id));
    }

    @Override
    public void forEachByChangeStatusesAndFileTypesAsc(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final ForkJoinPool threadPool,
            final Consumer<FileMetadata> consumer) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetchByChangeStatusesAndFileTypesAsc(id, changeStatuses, fileTypes)
                    .stream()
                    .parallel()
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public void forEachByChangeStatusesAndFileTypesDesc(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final ForkJoinPool threadPool,
            final Consumer<FileMetadata> consumer) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetchByChangeStatusesAndFileTypesDesc(id, changeStatuses, fileTypes)
                    .stream()
                    .parallel()
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public void forEachDuplicateOf(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final DuplicateHandlingStrategy strategy,
            final HashAlgorithm hashAlgorithm,
            final Consumer<List<List<FileMetadata>>> consumer) {
        final var buffer = new ArrayList<List<FileMetadata>>(BATCH_CHUNK_SIZE);
        if (strategy == DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP) {
            if (hashAlgorithm == HashAlgorithm.NONE) {
                jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
                    handle.fetchDuplicateIdsByTimestampOf(id, changeStatuses, fileTypes)
                            .stream()
                            .map(g -> fetchByIds(id, g))
                            .forEachOrdered(formBatches(consumer, buffer));
                    //process the final batch
                    consumer.accept(buffer);
                    return null;
                });
            } else {
                jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
                    handle.fetchDuplicateIdsByHashOf(id, changeStatuses, fileTypes)
                            .stream()
                            .map(g -> fetchByIds(id, g))
                            .forEachOrdered(formBatches(consumer, buffer));
                    //process the final batch
                    consumer.accept(buffer);
                    return null;
                });
            }
        } else {
            jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
                handle.fetchByChangeStatusesAndFileTypesDesc(id, changeStatuses, fileTypes)
                        .stream()
                        .map(Collections::singletonList)
                        .forEachOrdered(formBatches(consumer, buffer));
                //process the final batch
                consumer.accept(buffer);
                return null;
            });
        }
    }

    private Consumer<List<FileMetadata>> formBatches(
            final Consumer<List<List<FileMetadata>>> consumer,
            final ArrayList<List<FileMetadata>> buffer) {
        return duplicates -> {
            if (buffer.size() >= BATCH_CHUNK_SIZE) {
                consumer.accept(List.copyOf(buffer));
                buffer.clear();
            }
            buffer.add(duplicates);
        };
    }

    private List<FileMetadata> fetchByIds(
            final FileMetadataSetId id,
            final GroupedIdCollection collection) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFilesByIds(id, collection.ids()));
    }

    @Override
    public long getOriginalSizeBytes(final FileMetadataSetId id) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .getOriginalSizeBytes(id));
    }

    @Override
    public boolean containsFileId(
            final FileMetadataSetId id,
            final UUID fileId) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .containsFileId(id, fileId));
    }

    @Override
    public void forEachForIndex(
            final FileMetadataSetId id,
            final Consumer<FileMetadataIndex> consumer) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.findFileIndex(id)
                    .stream()
                    .forEach(consumer);
            return null;
        });
    }

    @Override
    public List<FileMetadata> findErrorsOf(final FileMetadataSetId id) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findErrorsOf(id));
    }

    @Override
    public void updateArchiveMetadataId(
            final FileMetadataSetId id,
            final UUID metadataId,
            final @Nullable UUID archiveMetadataId) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.updateArchiveMetadataId(id, metadataId, archiveMetadataId);
            return null;
        });
    }

    @Override
    public FileMetadataSetId intersectByPath(
            final FileMetadataSetId filesFromLastIncrement,
            final FileMetadataSetId restoreScope) {
        final var result = createFileSet();
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.intersectByPath(result, filesFromLastIncrement, restoreScope);
            return null;
        });
        return result;
    }

    @Override
    public FileMetadataSetId keepChangedContent(
            final FileMetadataSetId id,
            final BackupPathChangeStatusMapId changeStats) {
        final var result = createFileSet();
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.keepChangedContent(result, id, changeStats, FileType.allContentSources(), Change.allWithChangedContent());
            return null;
        });
        return result;
    }

    @Override
    public FileMetadataSetId keepChangedMetadata(
            final FileMetadataSetId id,
            final Set<FileType> fileTypes,
            final BackupPathChangeStatusMapId changeStats) {
        final var result = createFileSet();
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.keepChangedMetadata(result, id, fileTypes, changeStats);
            return null;
        });
        return result;
    }

    @Override
    public Optional<FileMetadata> findFileById(
            final UUID id,
            final UUID file) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFileById(id, file));
    }

    @Override
    public SortedSet<FileMetadata> findFilesByIds(
            final FileMetadataSetId id,
            final Set<UUID> files) {
        final var fileMetadata = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFilesByIds(id, files));
        return new TreeSet<>(fileMetadata);
    }

    @Override
    public boolean containsPath(
            final FileMetadataSetId id,
            final String absolutePath) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .containsPath(id, BackupPath.ofPathAsIs(absolutePath)));
    }

    @Override
    public FileMetadataSetId copyAllNotDeleted(final FileMetadataSetId source) {
        final var result = createFileSet();
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.copyAllNotDeleted(result, source);
            return null;
        });
        return result;
    }

    @Override
    public void copyAll(
            final FileMetadataSetId source,
            final FileMetadataSetId target) {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.copyAll(target, source);
            return null;
        });
    }

    @Override
    public boolean isClosed() {
        return openSets.isEmpty();
    }

    @Override
    public void assertExists(final FileMetadataSetId id) {
        if (!openSets.contains(id)) {
            throw new IllegalStateException(id.getClass().getSimpleName() + " " + id.id() + " does not exist.");
        }
    }

    @Override
    public void close() {
        jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> {
            handle.dropAll();
            return null;
        });
        openSets.clear();
    }

    protected DataStore dataStore() {
        return dataStore;
    }

    private FileMetadataSetId createFileSetId(final Consumer<FileMetadataSetId> closeWith) {
        final var id = new FileMetadataSetId(closeWith);
        openSets.add(id);
        return id;
    }
}
