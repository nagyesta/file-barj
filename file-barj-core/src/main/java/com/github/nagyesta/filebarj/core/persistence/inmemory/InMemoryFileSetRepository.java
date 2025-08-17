package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.FileSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.FileSetId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class InMemoryFileSetRepository implements FileSetRepository {

    private final Map<UUID, Collection<Path>> fileSets = new ConcurrentHashMap<>();

    @Override
    public FileSetId createFileSet() {
        final var fileSetId = new FileSetId(this::removeFileSet);
        fileSets.put(fileSetId.id(), new ConcurrentSkipListSet<>());
        return fileSetId;
    }

    @Override
    public void appendTo(
            final @NonNull FileSetId id,
            final @NonNull Path path) {
        appendTo(id, Collections.singletonList(path));
    }

    @Override
    public void appendTo(
            final @NonNull FileSetId id,
            final @NonNull Collection<Path> paths) {
        if (fileSets.containsKey(id.id())) {
            final var fileSet = fileSets.get(id.id());
            fileSet.addAll(paths);
        }
    }

    @Override
    public Optional<Path> takeFirst(final @NonNull FileSetId id) {
        final var paths = fileSets.get(id.id());
        final var iterator = paths.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        final var next = Optional.ofNullable(iterator.next());
        iterator.remove();
        return next;
    }

    @Override
    public void removeFileSet(final @NonNull FileSetId id) {
        fileSets.remove(id.id());
    }

    @Override
    public List<Path> findAll(
            final @NonNull FileSetId id,
            final long offset,
            final long limit,
            final @NonNull SortOrder order) {
        return fileSets.get(id.id()).stream()
                .sorted(orderBy(order))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countAll(final @NonNull FileSetId id) {
        return fileSets.get(id.id()).size();
    }

    @Override
    public boolean isEmpty(final @NonNull FileSetId id) {
        return countAll(id) == 0L;
    }

    @Override
    public List<Path> detectCaseInsensitivityIssues(final @NonNull FileSetId id) {
        return fileSets.get(id.id()).stream()
                .collect(Collectors.groupingBy(path -> path.toString().toLowerCase()))
                .values().stream()
                .filter(paths -> paths.size() > 1)
                .flatMap(Collection::stream)
                .sorted()
                .toList();
    }

    @Override
    public void forEach(
            final @NonNull FileSetId fileSetId,
            final @NonNull ForkJoinPool threadPool,
            final @NonNull SortOrder order,
            final @NonNull Consumer<Path> consumer) {
        final var countAll = countAll(fileSetId);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + Integer.MAX_VALUE)
                .mapToObj(offset -> findAll(fileSetId, offset, Integer.MAX_VALUE, order))
                .forEach(paths -> threadPool.submit(() -> paths.stream().parallel().forEach(consumer)).join());
    }

    private Comparator<Path> orderBy(final @NotNull SortOrder order) {
        return switch (order) {
            case ASC -> Comparator.naturalOrder();
            case DESC -> Comparator.reverseOrder();
        };
    }
}
