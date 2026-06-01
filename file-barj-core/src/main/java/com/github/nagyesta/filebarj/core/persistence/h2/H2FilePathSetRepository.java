package com.github.nagyesta.filebarj.core.persistence.h2;

import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2FilePathSetRepositoryExtension;
import org.jdbi.v3.core.Jdbi;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class H2FilePathSetRepository
        implements FilePathSetRepository {

    private static final long PAGE_SIZE = 1000L;

    private final Jdbi jdbi;
    private final Set<FilePathSetId> openSets = new CopyOnWriteArraySet<>();
    private DataStore dataStore;

    public H2FilePathSetRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public Optional<Path> takeFirst(final FilePathSetId id) {
        return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            final var pathList = handle.fetchPage(id, 1L, 0L);
            if (pathList.isEmpty()) {
                return Optional.empty();
            }
            final var path = pathList.get(0);
            handle.deleteOne(id, path);
            return Optional.of(Path.of(path));
        });
    }

    @Override
    public List<Path> detectCaseInsensitivityIssues(final FilePathSetId id) {
        return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> handle
                .detectCaseInsensitivityIssues(id)
                .stream()
                .map(Path::of)
                .toList());
    }

    @SuppressWarnings("resource")
    @Override
    public FilePathSetId subtract(
            final FilePathSetId fromSet,
            final FilePathSetId removeSet) {
        final var result = createFileSet();
        return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            handle.subtract(result, fromSet, removeSet);
            return result;
        });
    }

    @Override
    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public FilePathSetId createFileSet() {
        return createFileSetId(this::removeFileSet);
    }

    @Override
    public void appendTo(
            final FilePathSetId id,
            final Path value) {
        appendTo(id, Collections.singletonList(value));
    }

    @Override
    public void appendTo(
            final FilePathSetId id,
            final Collection<Path> values) {
        jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            final var allPathsAsString = values.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
            final var found = handle.exists(id, allPathsAsString);
            for (final var pathAsString : allPathsAsString) {
                if (!found.contains(pathAsString)) {
                    handle.appendTo(id, pathAsString);
                }
            }
            return null;
        });
    }

    @Override
    public void removeFileSet(final FilePathSetId id) {
        jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            handle.deleteAll(id);
            return null;
        });
    }

    @Override
    public List<Path> findAll(
            final FilePathSetId id,
            final long offset,
            final long limit,
            final SortOrder order) {
        if (order == SortOrder.ASC) {
            return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> handle
                    .fetchPage(id, limit, offset)
                    .stream()
                    .map(Path::of)
                    .toList());
        } else {
            return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> handle
                    .fetchPageReverse(id, limit, offset)
                    .stream()
                    .map(Path::of)
                    .toList());
        }
    }

    @Override
    public long countAll(final FilePathSetId id) {
        return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> handle
                .countAll(id));
    }

    @Override
    public boolean isEmpty(final FilePathSetId id) {
        return !jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> handle
                .exists(id));
    }

    @Override
    public void forEach(
            final FilePathSetId id,
            final ForkJoinPool threadPool,
            final Consumer<Path> consumer) {
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                .mapToObj(offset -> findAll(id, offset, PAGE_SIZE, SortOrder.ASC))
                .forEach(values -> threadPool.submit(() -> values.stream().parallel().forEach(consumer)).join());
    }

    @Override
    public void forEachOrdered(
            final FilePathSetId id,
            final ForkJoinPool threadPool,
            final SortOrder order,
            final Consumer<Path> consumer) {
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                .mapToObj(offset -> findAll(id, offset, PAGE_SIZE, order))
                .forEachOrdered(values -> threadPool.submit(() -> values.stream().parallel().forEachOrdered(consumer)).join());
    }

    @Override
    public boolean isClosed() {
        return openSets.isEmpty();
    }

    @Override
    public void assertExists(final FilePathSetId id) {
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

    private FilePathSetId createFileSetId(final Consumer<FilePathSetId> closeWith) {
        final var id = new FilePathSetId(closeWith);
        openSets.add(id);
        return id;
    }
}
