package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;

import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface BaseFileMapRepository<K extends BaseFileSetId<K>, U extends Comparable<U>, V extends Comparable<V>> {

    K createFileMap();

    void appendTo(K id, U key, V value);

    void appendTo(K id, Map<U, V> values);

    void removeFileMap(K id);

    default SortedMap<U, V> findAll(final K id, final long offset, final long limit) {
        return findAll(id, offset, limit, SortOrder.ASC);
    }

    SortedMap<U, V> findAll(K id, long offset, long limit, SortOrder sortOrder);

    long countAll(K id);

    boolean isEmpty(K id);

    default void forEach(final K id, final ForkJoinPool threadPool, final Consumer<Map.Entry<U, V>> consumer) {
        forEach(id, threadPool, SortOrder.ASC, consumer);
    }

    default void forEachReverse(final K id, final ForkJoinPool threadPool, final Consumer<Map.Entry<U, V>> consumer) {
        forEach(id, threadPool, SortOrder.DESC, consumer);
    }

    void forEach(K id, ForkJoinPool threadPool, SortOrder order, Consumer<Map.Entry<U, V>> consumer);

}

