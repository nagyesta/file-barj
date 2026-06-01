package com.github.nagyesta.filebarj.core.persistence.h2;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2FileMetadataSetRepositoryExtension;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class H2FileMetadataSetRepository
        implements FileMetadataSetRepository {

    private static final long PAGE_SIZE = 1000L;

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
            for (final var entity : values) {
                handle.appendTo(id, entity);
            }
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

    @Override
    public List<FileMetadata> findAll(
            final FileMetadataSetId id,
            final long offset,
            final long limit,
            final SortOrder order) {
        if (order == SortOrder.ASC) {
            return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                    .fetchPage(id, limit, offset)
            );
        } else {
            return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                    .fetchPageReverse(id, limit, offset)
            );
        }
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
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                .mapToObj(offset -> findAll(id, offset, PAGE_SIZE, SortOrder.ASC))
                .forEach(values -> threadPool.submit(() -> values.stream().parallel().forEach(consumer)).join());
    }

    @Override
    public void forEachOrdered(
            final FileMetadataSetId id,
            final ForkJoinPool threadPool,
            final SortOrder order,
            final Consumer<FileMetadata> consumer) {
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                .mapToObj(offset -> findAll(id, offset, PAGE_SIZE, order))
                .forEachOrdered(values -> threadPool.submit(() -> values.stream().parallel().forEachOrdered(consumer)).join());
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
    public void forEachByChangeStatusesAndFileTypes(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final ForkJoinPool threadPool,
            final SortOrder order,
            final Consumer<FileMetadata> consumer) {
        final var countAll = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .countByChangeStatusesAndTypes(id, changeStatuses, fileTypes));
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                .mapToObj(offset -> fetchPageByChangeStatusesAndFileTypes(id, changeStatuses, fileTypes, offset, order))
                .forEachOrdered(values -> threadPool.submit(() -> values.stream().parallel().forEachOrdered(consumer)).join());
    }

    private List<FileMetadata> fetchPageByChangeStatusesAndFileTypes(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final long offset,
            final SortOrder order) {
        if (order == SortOrder.ASC) {
            return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                    .fetchPageByChangeStatusesAndFileTypes(id, changeStatuses, fileTypes, PAGE_SIZE, offset));
        } else {
            return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                    .fetchPageByChangeStatusesAndFileTypesReverse(id, changeStatuses, fileTypes, PAGE_SIZE, offset));
        }
    }

    @Override
    public void forEachDuplicateOf(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final DuplicateHandlingStrategy strategy,
            final HashAlgorithm hashAlgorithm,
            final ForkJoinPool threadPool,
            final Consumer<List<List<FileMetadata>>> consumer) {
        if (strategy == DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP) {
            if (hashAlgorithm == HashAlgorithm.NONE) {
                final var countAll = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                        .countDuplicateKeysByTimestampOf(id, changeStatuses, fileTypes));
                LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                        .mapToObj(offset -> fetchDuplicateByTimestamp(id, changeStatuses, fileTypes, offset))
                        .forEachOrdered(values -> threadPool.submit(() -> values.stream().parallel().forEachOrdered(consumer)).join());
            } else {
                final var countAll = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                        .countDuplicateKeysByHashOf(id, changeStatuses, fileTypes));
                LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                        .mapToObj(offset -> fetchDuplicateByHash(id, changeStatuses, fileTypes, offset))
                        .forEachOrdered(values -> threadPool.submit(() -> values.stream().parallel().forEachOrdered(consumer)).join());
            }
        } else {
            final var countAll = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                    .countByChangeStatusesAndTypes(id, changeStatuses, fileTypes));
            LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                    .mapToObj(offset -> fetchFilesForEachDuplicate(id, changeStatuses, fileTypes, offset))
                    .forEachOrdered(values -> threadPool.submit(() -> values.stream().parallel().forEachOrdered(consumer)).join());
        }
    }

    private List<List<List<FileMetadata>>> fetchDuplicateByHash(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final long offset) {
        final var idMap = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .fetchPageOfDuplicateIdsByHashOf(id, changeStatuses, fileTypes, PAGE_SIZE, offset));
        return fetchByIdMap(id, idMap);
    }

    private List<List<List<FileMetadata>>> fetchDuplicateByTimestamp(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final long offset) {
        final var idMap = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .fetchPageOfDuplicateIdsByTimestampOf(id, changeStatuses, fileTypes, PAGE_SIZE, offset));
        return fetchByIdMap(id, idMap);
    }

    private List<List<List<FileMetadata>>> fetchFilesForEachDuplicate(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final long offset) {
        return fetchPageByChangeStatusesAndFileTypes(id, changeStatuses, fileTypes, offset, SortOrder.DESC)
                .stream()
                .map(List::of)
                .map(List::of)
                .toList();
    }

    private List<List<List<FileMetadata>>> fetchByIdMap(
            final FileMetadataSetId id,
            final Map<String, List<UUID>> idMap) {
        final var allIds = idMap.values().stream().flatMap(Collection::stream).toList();
        final var fileMetadataByIds = jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                        .findFilesByIds(id, allIds))
                .stream()
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity()));
        return idMap.values().stream()
                .map(ids -> ids.stream()
                        .map(fileMetadataByIds::get)
                        .toList())
                .map(List::of)
                .toList();
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
    public Set<FileMetadata> findFilesByOriginalHash(
            final FileMetadataSetId id,
            final String originalHash) {
        return new LinkedHashSet<>(jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFilesByOriginalHash(id, originalHash, Integer.MAX_VALUE, 0)));
    }

    @Override
    public Set<FileMetadata> findFilesByOriginalSize(
            final FileMetadataSetId id,
            final Long originalSize) {
        return new LinkedHashSet<>(jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFilesByOriginalSize(id, originalSize, Integer.MAX_VALUE, 0)));
    }

    @Override
    public Optional<FileMetadata> findFileByPath(
            final FileMetadataSetId id,
            final BackupPath absolutePath) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFileByPath(id, absolutePath));
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
        openSets.forEach(this::removeFileSet);
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
