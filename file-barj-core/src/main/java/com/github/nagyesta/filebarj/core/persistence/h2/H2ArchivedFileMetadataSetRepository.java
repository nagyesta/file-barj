package com.github.nagyesta.filebarj.core.persistence.h2;

import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.persistence.ArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.ArchiveFileReference;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2ArchivedFileMetadataSetRepositoryExtension;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2FileMetadataSetRepositoryExtension;
import org.jdbi.v3.core.Jdbi;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class H2ArchivedFileMetadataSetRepository
        implements ArchivedFileMetadataSetRepository {

    private final Jdbi jdbi;
    private final Set<ArchivedFileMetadataSetId> openSets = new CopyOnWriteArraySet<>();
    private DataStore dataStore;

    public H2ArchivedFileMetadataSetRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public ArchivedFileMetadataSetId createFileSet() {
        return createFileSetId(this::removeFileSet);
    }

    @Override
    public void appendTo(
            final ArchivedFileMetadataSetId id,
            final ArchivedFileMetadata value) {
        appendTo(id, Collections.singletonList(value));
    }

    @Override
    public void appendTo(
            final ArchivedFileMetadataSetId id,
            final Collection<ArchivedFileMetadata> values) {
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            handle.appendTo(id, values);
            final var files = values.stream()
                    .flatMap(entity -> entity.getFiles()
                            .stream()
                            .map(file -> new ArchiveFileReference(
                                    entity.getId(),
                                    entity.getArchiveLocation().getBackupIncrement(),
                                    file)))
                    .toList();
            handle.appendFileTo(id, files);
            return null;
        });
    }

    @Override
    public void removeFileSet(final ArchivedFileMetadataSetId id) {
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            handle.deleteAll(id);
            return null;
        });
    }

    @Override
    public long countAll(final ArchivedFileMetadataSetId id) {
        return jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> handle
                .countAll(id));
    }

    @Override
    public boolean isEmpty(final ArchivedFileMetadataSetId id) {
        return !jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> handle
                .exists(id));
    }

    @Override
    public void forEach(
            final ArchivedFileMetadataSetId id,
            final ForkJoinPool threadPool,
            final Consumer<ArchivedFileMetadata> consumer) {
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetch(id)
                    .stream()
                    .parallel()
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public void forEachAsc(
            final ArchivedFileMetadataSetId id,
            final ForkJoinPool threadPool,
            final Consumer<ArchivedFileMetadata> consumer) {
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetchAsc(id)
                    .stream()
                    .parallel()
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public long countAllFiles(final ArchivedFileMetadataSetId id) {
        return jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> handle
                .countAllFiles(id));
    }

    @Override
    public List<ArchivedFileMetadata> findAll(
            final ArchivedFileMetadataSetId id) {
        return jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> handle
                .fetchAsc(id)
                .collectIntoList());
    }

    @Override
    public Optional<ArchivedFileMetadata> findByFileMetadataId(
            final ArchivedFileMetadataSetId id,
            final UUID fileMetadataId) {
        return jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> handle
                .findByFileMetadataId(id, fileMetadataId));
    }

    @Override
    public ArchivedFileMetadataSetId intersectWithFileMetadata(
            final ArchivedFileMetadataSetId id,
            final FileMetadataSetId fileMetadataSetId) {
        final var result = createFileSet();
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            handle.intersectWithFileMetadata(result, id, fileMetadataSetId);
            handle.copyMatchingFileReferences(result, id, fileMetadataSetId);
            return null;
        });
        return result;
    }

    @Override
    public ArchivedFileMetadataSetId filterByBackupIncrements(
            final ArchivedFileMetadataSetId id,
            final SortedSet<Integer> versions) {
        final var result = createFileSet();
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            handle.filterByBackupIncrements(result, id, versions);
            handle.copyFileReferencesOfVersions(result, id, versions);
            return null;
        });
        return result;
    }

    @Override
    public Set<String> asEntryPaths(final ArchivedFileMetadataSetId id) {
        return jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> handle
                .asEntryPaths(id));
    }

    @Override
    public SortedSet<FileMetadata> findFileMetadataByArchiveLocator(
            final ArchivedFileMetadataSetId id,
            final FileMetadataSetId files,
            final ArchiveEntryLocator currentLocator) {
        return jdbi.withExtension(H2FileMetadataSetRepositoryExtension.class, handle -> handle
                .findFileMetadataByArchiveLocator(id, files, currentLocator));
    }

    @Override
    public ArchivedFileMetadataSetId copyAll(final ArchivedFileMetadataSetId source) {
        final var result = createFileSet();
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            handle.copyAll(result, source);
            handle.copyAllFileReferences(result, source);
            return null;
        });
        return result;
    }

    @Override
    public boolean isClosed() {
        return openSets.isEmpty();
    }

    @Override
    public void assertExists(final ArchivedFileMetadataSetId id) {
        if (!openSets.contains(id)) {
            throw new IllegalStateException(id.getClass().getSimpleName() + " " + id.id() + " does not exist.");
        }
    }

    @Override
    public void close() {
        jdbi.withExtension(H2ArchivedFileMetadataSetRepositoryExtension.class, handle -> {
            handle.dropAllFiles();
            handle.dropAll();
            return null;
        });
        openSets.clear();
    }

    protected DataStore dataStore() {
        return dataStore;
    }

    private ArchivedFileMetadataSetId createFileSetId(final Consumer<ArchivedFileMetadataSetId> closeWith) {
        final var id = new ArchivedFileMetadataSetId(closeWith);
        openSets.add(id);
        return id;
    }

}
