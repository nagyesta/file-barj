package com.github.nagyesta.filebarj.io.stream.index;

import com.github.nagyesta.filebarj.io.stream.IndexVersion;
import com.github.nagyesta.filebarj.io.stream.ReadOnlyArchiveIndex;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.COLON;
import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.LINE_BREAK;

@Getter
public class ArchiveIndexV2 implements ReadOnlyArchiveIndex {

    private static final String LAST_ENTITY_INDEX_PROPERTY = "last.entity.index";
    private static final String LAST_CHUNK_INDEX_PROPERTY = "last.chunk.index";
    private static final String LAST_CHUNK_SIZE_PROPERTY = "last.chunk.size";
    private static final String MAX_CHUNK_SIZE_PROPERTY = "max.chunk.size";
    private static final String TOTAL_SIZE_PROPERTY = "total.size";
    private final Properties properties;
    private final IndexVersion indexVersion;
    private final long totalEntities;
    private final int numberOfChunks;
    private final long maxChunkSizeInBytes;
    private final long lastChunkSizeInBytes;
    private final long totalSize;

    public ArchiveIndexV2(@NotNull final Properties properties) {
        this.properties = properties;
        this.indexVersion = IndexVersion.forVersionString(properties.getProperty(INDEX_VERSION));
        this.totalEntities = Long.parseLong(properties.getProperty(LAST_ENTITY_INDEX_PROPERTY));
        this.numberOfChunks = Integer.parseInt(properties.getProperty(LAST_CHUNK_INDEX_PROPERTY));
        this.maxChunkSizeInBytes = Long.parseLong(properties.getProperty(MAX_CHUNK_SIZE_PROPERTY));
        this.lastChunkSizeInBytes = Long.parseLong(properties.getProperty(LAST_CHUNK_SIZE_PROPERTY));
        this.totalSize = Long.parseLong(properties.getProperty(TOTAL_SIZE_PROPERTY));
    }

    @Builder
    public ArchiveIndexV2(
            final long totalSize,
            final long lastChunkSizeInBytes,
            final long maxChunkSizeInBytes,
            final int numberOfChunks,
            final long totalEntities) {
        this.indexVersion = IndexVersion.V2;
        this.properties = null;
        this.totalSize = totalSize;
        this.lastChunkSizeInBytes = lastChunkSizeInBytes;
        this.maxChunkSizeInBytes = maxChunkSizeInBytes;
        this.numberOfChunks = numberOfChunks;
        this.totalEntities = totalEntities;
    }

    @Override
    public BarjCargoEntityIndex entity(@NotNull final String prefix) {
        return BarjCargoEntityIndex.fromProperties(properties, prefix);
    }

    public String footerAsString() {
        return LAST_CHUNK_INDEX_PROPERTY + COLON + numberOfChunks + LINE_BREAK
                + LAST_CHUNK_SIZE_PROPERTY + COLON + lastChunkSizeInBytes + LINE_BREAK
                + MAX_CHUNK_SIZE_PROPERTY + COLON + maxChunkSizeInBytes + LINE_BREAK
                + LAST_ENTITY_INDEX_PROPERTY + COLON + totalEntities + LINE_BREAK
                + TOTAL_SIZE_PROPERTY + COLON + totalSize + LINE_BREAK
                + INDEX_VERSION + COLON + indexVersion.getVersion() + LINE_BREAK;
    }
}
