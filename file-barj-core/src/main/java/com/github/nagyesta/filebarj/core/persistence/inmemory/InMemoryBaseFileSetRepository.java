package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public abstract class InMemoryBaseFileSetRepository<K extends BaseFileSetId<K>, V extends Comparable<V>>
        implements Closeable {

    private final Map<UUID, Collection<V>> fileSets = new ConcurrentHashMap<>();
    private DataStore dataStore;

    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public K createFileSet() {
        final var id = createFileSetId(this::removeFileSet);
        fileSets.put(id.id(), new ConcurrentSkipListSet<>());
        return id;
    }

    public void appendTo(
            final @NotNull K id,
            final @NotNull V value) {
        appendTo(id, Collections.singletonList(value));
    }

    public void appendTo(
            final @NotNull K id,
            final @NotNull Collection<V> values) {
        if (fileSets.containsKey(id.id())) {
            final var fileSet = getFileSetById(id);
            fileSet.addAll(values);
        }
    }

    public void removeFileSet(final @NotNull K id) {
        fileSets.remove(id.id());
    }

    public List<V> findAll(final @NotNull K id) {
        return fileSets.get(id.id()).stream()
                .sorted(orderBy(SortOrder.ASC))
                .toList();
    }

    public long countAll(final @NotNull K id) {
        final var map = getFileSetById(id);
        if (map == null) {
            return 0L;
        }
        return map.size();
    }

    public boolean isEmpty(final @NotNull K id) {
        return countAll(id) == 0L;
    }

    public void forEach(
            final @NotNull K id,
            final @NotNull ForkJoinPool threadPool,
            final @NotNull Consumer<V> consumer) {
        threadPool.submit(() -> fileSets.get(id.id()).stream()
                .parallel()
                .forEach(consumer)).join();
    }

    public void forEachAsc(
            final @NotNull K id,
            final @NotNull ForkJoinPool threadPool,
            final @NotNull Consumer<V> consumer) {
        threadPool.submit(() -> fileSets.get(id.id()).stream()
                .sorted(orderBy(SortOrder.ASC))
                .parallel()
                .forEachOrdered(consumer)).join();
    }

    public void forEachDesc(
            final @NotNull K id,
            final @NotNull ForkJoinPool threadPool,
            final @NotNull Consumer<V> consumer) {
        threadPool.submit(() -> fileSets.get(id.id()).stream()
                .sorted(orderBy(SortOrder.DESC))
                .parallel()
                .forEachOrdered(consumer)).join();
    }

    public boolean isClosed() {
        return fileSets.isEmpty();
    }

    public void assertExists(final @NotNull K id) {
        if (getFileSetById(id) == null) {
            throw new IllegalStateException(id.getClass().getSimpleName() + " " + id.id() + " does not exist.");
        }
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
