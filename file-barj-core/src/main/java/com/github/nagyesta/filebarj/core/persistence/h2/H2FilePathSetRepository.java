package com.github.nagyesta.filebarj.core.persistence.h2;

import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2FilePathSetRepositoryExtension;
import org.jdbi.v3.core.Jdbi;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class H2FilePathSetRepository
        implements FilePathSetRepository {

    private final Jdbi jdbi;
    private final Set<FilePathSetId> openSets = new CopyOnWriteArraySet<>();
    private DataStore dataStore;

    public H2FilePathSetRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
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
                    .collect(Collectors.toCollection(HashSet::new));
            handle.appendTo(id, allPathsAsString);
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

    public List<Path> findAll(final FilePathSetId id) {
        return jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> handle
                .fetchAsc(id)
                .stream()
                .map(Path::of)
                .toList());
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
        threadPool.submit(() -> findAll(id)
                .stream()
                .parallel().forEach(consumer)).join();
    }

    @Override
    public void forEachAsc(
            final FilePathSetId id,
            final ForkJoinPool threadPool,
            final Consumer<Path> consumer) {
        jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetchAsc(id)
                    .stream()
                    .parallel()
                    .map(Path::of)
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public void forEachDesc(
            final FilePathSetId id,
            final ForkJoinPool threadPool,
            final Consumer<Path> consumer) {
        jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            threadPool.submit(() -> handle.fetchDesc(id)
                    .stream()
                    .parallel()
                    .map(Path::of)
                    .forEachOrdered(consumer)).join();
            return null;
        });
    }

    @Override
    public boolean isClosed() {
        return openSets.isEmpty();
    }

    @Override
    public void close() {
        jdbi.withExtension(H2FilePathSetRepositoryExtension.class, handle -> {
            handle.dropAll();
            return null;
        });
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
