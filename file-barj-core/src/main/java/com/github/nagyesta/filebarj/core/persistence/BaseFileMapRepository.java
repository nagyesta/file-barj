package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;

import java.io.Closeable;
import java.util.Map;

public interface BaseFileMapRepository<K extends BaseFileSetId<K>, U extends Comparable<U>, V extends Comparable<V>>
        extends Closeable {

    void registerWith(DataStore dataStore);

    K createFileMap();

    void appendTo(K id, U key, V value);

    void appendTo(K id, Map<U, V> values);

    void removeFileMap(K id);

    boolean isClosed();

}

