package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;

/**
 * A read-only representation of an archive index file.
 */
public interface ReadOnlyArchiveIndex {

    /**
     * The name of the property that contains the version of the index specification.
     */
    String INDEX_VERSION = "version";

    int getNumberOfChunks();

    long getMaxChunkSizeInBytes();

    long getLastChunkSizeInBytes();

    long getTotalSize();

    long getTotalEntities();

    BarjCargoEntityIndex entity(String prefix);
}
