package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface BaseFileSetRepository<K extends BaseFileSetId<K>, V extends Comparable<V>>
        extends Closeable {

    void registerWith(DataStore dataStore);

    K createFileSet();

    void appendTo(K id, V value);

    void appendTo(K id, Collection<V> values);

    void removeFileSet(K id);

    default List<V> findAll(final K id, final long offset, final long limit) {
        return findAll(id, offset, limit, SortOrder.ASC);
    }

    List<V> findAll(K id, long offset, long limit, SortOrder sortOrder);

    long countAll(K id);

    boolean isEmpty(K id);

    default void forEach(final K id, final ForkJoinPool threadPool, final Consumer<V> consumer) {
        forEach(id, threadPool, SortOrder.ASC, consumer);
    }

    default void forEachReverse(final K id, final ForkJoinPool threadPool, final Consumer<V> consumer) {
        forEach(id, threadPool, SortOrder.DESC, consumer);
    }

    void forEach(K id, ForkJoinPool threadPool, SortOrder order, Consumer<V> consumer);

    boolean isClosed();

}

