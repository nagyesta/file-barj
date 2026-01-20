package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.BaseFileSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.LongStream;

public abstract class InMemoryBaseFileSetRepository<K extends BaseFileSetId<K>, V extends Comparable<V>>
        implements BaseFileSetRepository<K, V> {

    private final Map<UUID, Collection<V>> fileSets = new ConcurrentHashMap<>();

    @Override
    public K createFileSet() {
        final var id = createFileSetId(this::removeFileSet);
        fileSets.put(id.id(), new ConcurrentSkipListSet<>());
        return id;
    }

    @Override
    public void appendTo(
            final @NonNull K id,
            final @NonNull V value) {
        appendTo(id, Collections.singletonList(value));
    }

    @Override
    public void appendTo(
            final @NonNull K id,
            final @NonNull Collection<V> values) {
        if (fileSets.containsKey(id.id())) {
            final var fileSet = getFileSetById(id);
            fileSet.addAll(values);
        }
    }

    @Override
    public Optional<V> takeFirst(final @NonNull K id) {
        final var values = getFileSetById(id);
        final var iterator = values.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        final var next = Optional.ofNullable(iterator.next());
        iterator.remove();
        return next;
    }

    @Override
    public void removeFileSet(final @NonNull K id) {
        fileSets.remove(id.id());
    }

    @Override
    public List<V> findAll(
            final @NonNull K id,
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
    public long countAll(final @NonNull K id) {
        return getFileSetById(id).size();
    }

    @Override
    public boolean isEmpty(final @NonNull K id) {
        return countAll(id) == 0L;
    }

    @Override
    public void forEach(
            final @NonNull K id,
            final @NonNull ForkJoinPool threadPool,
            final @NonNull SortOrder order,
            final @NonNull Consumer<V> consumer) {
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + Integer.MAX_VALUE)
                .mapToObj(offset -> findAll(id, offset, Integer.MAX_VALUE, order))
                .forEach(values -> threadPool.submit(() -> values.stream().parallel().forEach(consumer)).join());
    }

    protected abstract K createFileSetId(Consumer<K> closeWith);

    protected Collection<V> getFileSetById(final @NonNull K id) {
        return fileSets.get(id.id());
    }

    private Comparator<V> orderBy(final @NotNull SortOrder order) {
        return switch (order) {
            case ASC -> Comparator.naturalOrder();
            case DESC -> Comparator.reverseOrder();
        };
    }
}
