package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.BaseFileSetRepository;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
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
    private DataStore dataStore;

    @Override
    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public K createFileSet() {
        final var id = createFileSetId(this::removeFileSet);
        fileSets.put(id.id(), new ConcurrentSkipListSet<>());
        return id;
    }

    @Override
    public void appendTo(
            final @NotNull K id,
            final @NotNull V value) {
        appendTo(id, Collections.singletonList(value));
    }

    @Override
    public void appendTo(
            final @NotNull K id,
            final @NotNull Collection<V> values) {
        if (fileSets.containsKey(id.id())) {
            final var fileSet = getFileSetById(id);
            fileSet.addAll(values);
        }
    }

    @Override
    public void removeFileSet(final @NotNull K id) {
        fileSets.remove(id.id());
    }

    @Override
    public List<V> findAll(
            final @NotNull K id,
            final long offset,
            final long limit,
            final @NotNull SortOrder order) {
        return fileSets.get(id.id()).stream()
                .sorted(orderBy(order))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countAll(final @NotNull K id) {
        final var map = getFileSetById(id);
        if (map == null) {
            return 0L;
        }
        return map.size();
    }

    @Override
    public boolean isEmpty(final @NotNull K id) {
        return countAll(id) == 0L;
    }

    @Override
    public void forEach(
            final @NotNull K id,
            final @NotNull ForkJoinPool threadPool,
            final @NotNull SortOrder order,
            final @NotNull Consumer<V> consumer) {
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + Integer.MAX_VALUE)
                .mapToObj(offset -> findAll(id, offset, Integer.MAX_VALUE, order))
                .forEach(values -> threadPool.submit(() -> values.stream().parallel().forEach(consumer)).join());
    }

    @Override
    public boolean isClosed() {
        return fileSets.isEmpty();
    }

    @Override
    public void close() {
        fileSets.clear();
    }

    protected DataStore dataStore() {
        return dataStore;
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
