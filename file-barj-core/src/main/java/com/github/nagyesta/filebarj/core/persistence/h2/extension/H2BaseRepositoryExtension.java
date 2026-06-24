package com.github.nagyesta.filebarj.core.persistence.h2.extension;

import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;
import org.jdbi.v3.core.result.ResultIterable;

import java.util.Collection;

public interface H2BaseRepositoryExtension<K extends BaseFileSetId<K>, L, V> {

    void deleteAll(K id);

    void dropAll();

    void deleteOne(K id, L valueKey);

    boolean exists(K id);

    ResultIterable<V> fetch(K id);

    ResultIterable<V> fetchAsc(K id);

    ResultIterable<V> fetchDesc(K id);

    long countAll(K id);

    void appendTo(K id, Collection<V> value);
}
