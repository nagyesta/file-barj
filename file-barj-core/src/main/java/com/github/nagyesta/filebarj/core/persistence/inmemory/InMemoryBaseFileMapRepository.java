package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.BaseFileMapRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.LongStream;

public abstract class InMemoryBaseFileMapRepository<K extends BaseFileSetId<K>, U extends Comparable<U>, V extends Comparable<V>>
        implements BaseFileMapRepository<K, U, V> {

    private final Map<UUID, Map<U, V>> fileMaps = new ConcurrentHashMap<>();

    @Override
    public K createFileMap() {
        final var id = createFileMapId(this::removeFileMap);
        fileMaps.put(id.id(), new ConcurrentHashMap<>());
        return id;
    }

    @Override
    public void appendTo(
            final @NonNull K id,
            final @NonNull U key,
            final @NonNull V value) {
        appendTo(id, Map.of(key, value));
    }

    @Override
    public void appendTo(
            final @NonNull K id,
            final @NonNull Map<U, V> values) {
        if (fileMaps.containsKey(id.id())) {
            final var fileMap = getFileMapById(id);
            fileMap.putAll(values);
        }
    }

    @Override
    public void removeFileMap(final @NonNull K id) {
        fileMaps.remove(id.id());
    }

    @Override
    public SortedMap<U, V> findAll(
            final @NonNull K id,
            final long offset,
            final long limit,
            final @NonNull SortOrder order) {
        final var result = new TreeMap<U, V>();
        fileMaps.get(id.id()).entrySet()
                .stream()
                .sorted(orderBy(order))
                .skip(offset)
                .limit(limit)
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    @Override
    public long countAll(final @NonNull K id) {
        return getFileMapById(id).size();
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
            final @NonNull Consumer<Map.Entry<U, V>> consumer) {
        final var countAll = countAll(id);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + Integer.MAX_VALUE)
                .mapToObj(offset -> findAll(id, offset, Integer.MAX_VALUE, order))
                .forEach(values -> threadPool.submit(() -> values.entrySet().stream().parallel().forEach(consumer)).join());
    }

    protected abstract K createFileMapId(Consumer<K> closeWith);

    protected Map<U, V> getFileMapById(final @NonNull K id) {
        return fileMaps.get(id.id());
    }

    private Comparator<Map.Entry<U, V>> orderBy(final @NotNull SortOrder order) {
        return switch (order) {
            case ASC -> Map.Entry.comparingByKey();
            case DESC -> Map.Entry.comparingByKey(Comparator.reverseOrder());
        };
    }
}
