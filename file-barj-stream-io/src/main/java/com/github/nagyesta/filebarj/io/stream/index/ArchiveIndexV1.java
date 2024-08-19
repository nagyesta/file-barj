package com.github.nagyesta.filebarj.io.stream.index;

import com.github.nagyesta.filebarj.io.stream.IndexVersion;
import com.github.nagyesta.filebarj.io.stream.ReadOnlyArchiveIndex;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

@Getter
public class ArchiveIndexV1 implements ReadOnlyArchiveIndex {

    private static final String LAST_ENTITY_INDEX_PROPERTY = "last.entity.index";
    private static final String LAST_CHUNK_INDEX_PROPERTY = "last.cnunk.index";
    private static final String LAST_CHUNK_SIZE_PROPERTY = "last.cnunk.size";
    private static final String MAX_CHUNK_SIZE_PROPERTY = "max.cnunk.size";
    private static final String TOTAL_SIZE_PROPERTY = "total.size";
    private final Properties properties;
    private final IndexVersion indexVersion;
    private final long totalEntities;
    private final int numberOfChunks;
    private final long maxChunkSizeInBytes;
    private final long lastChunkSizeInBytes;
    private final long totalSize;

    public ArchiveIndexV1(final @NotNull Properties properties) {
        this.properties = properties;
        this.indexVersion = IndexVersion.forVersionString(properties.getProperty(INDEX_VERSION));
        this.totalEntities = Long.parseLong(properties.getProperty(LAST_ENTITY_INDEX_PROPERTY));
        this.numberOfChunks = Integer.parseInt(properties.getProperty(LAST_CHUNK_INDEX_PROPERTY));
        this.maxChunkSizeInBytes = Long.parseLong(properties.getProperty(MAX_CHUNK_SIZE_PROPERTY));
        this.lastChunkSizeInBytes = Long.parseLong(properties.getProperty(LAST_CHUNK_SIZE_PROPERTY));
        this.totalSize = Long.parseLong(properties.getProperty(TOTAL_SIZE_PROPERTY));
    }

    @Override
    public BarjCargoEntityIndex entity(final @NotNull String prefix) {
        return BarjCargoEntityIndex.fromProperties(properties, prefix);
    }
}
