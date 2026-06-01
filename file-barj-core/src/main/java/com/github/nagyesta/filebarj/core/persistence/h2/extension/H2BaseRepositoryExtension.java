package com.github.nagyesta.filebarj.core.persistence.h2.extension;

import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;

import java.util.List;

public interface H2BaseRepositoryExtension<K extends BaseFileSetId<K>, L, V> {

    void deleteAll(K id);

    void deleteOne(K id, L valueKey);

    boolean exists(K id);

    List<V> fetchPage(K id, long limit, long offset);

    List<V> fetchPageReverse(K id, long limit, long offset);

    long countAll(K id);

    void appendTo(K id, V value);
}
