package com.github.nagyesta.filebarj.io.stream.internal.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Optional;
import java.util.Properties;

import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.COLON;
import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.LINE_BREAK;
import static java.lang.Long.parseLong;

/**
 * POJO class representing the implicitly collected metadata for each chunk of the BaRJ cargo entry
 * it belongs to. The most important properties are the ones that marking the chunk boundaries
 * inside the stream of archived data.
 */
@Builder
@Data
public class BarjCargoEntryBoundaries {

    static final String REL_START_IDX = ".rel.start.idx";
    static final String REL_START_FILE = ".rel.start.file";
    static final String REL_END_IDX = ".rel.end.idx";
    static final String REL_END_FILE = ".rel.end.file";
    static final String ABS_START_IDX = ".abs.start.idx";
    static final String ABS_END_IDX = ".abs.end.idx";
    static final String ORIG_SIZE = ".orig.size";
    static final String ORIG_HASH = ".orig.hash";
    static final String ARCH_SIZE = ".arch.size";
    static final String ARCH_HASH = ".arch.hash";

    private final long chunkRelativeStartIndexInclusive;
    private final long chunkRelativeEndIndexExclusive;
    @NonNull
    private final String startChunkName;
    @NonNull
    private final String endChunkName;
    private final long absoluteStartIndexInclusive;
    private final long absoluteEndIndexExclusive;
    private final String originalHash;
    private final long originalSizeBytes;
    private final String archivedHash;
    private final long archivedSizeBytes;

    /**
     * Generates a partial properties snippet for the content or metadata part of the entity
     * (whichever is applicable for this object).
     *
     * @param prefix prefix for the property names
     * @return properties formatted data
     */
    public String toProperties(final String prefix) {
        return prefix + REL_START_IDX + COLON + chunkRelativeStartIndexInclusive + LINE_BREAK
                + prefix + REL_START_FILE + COLON + startChunkName + LINE_BREAK
                + prefix + REL_END_IDX + COLON + chunkRelativeEndIndexExclusive + LINE_BREAK
                + prefix + REL_END_FILE + COLON + endChunkName + LINE_BREAK
                + prefix + ABS_START_IDX + COLON + absoluteStartIndexInclusive + LINE_BREAK
                + prefix + ABS_END_IDX + COLON + absoluteEndIndexExclusive + LINE_BREAK
                + prefix + ORIG_SIZE + COLON + originalSizeBytes + LINE_BREAK
                + prefix + ORIG_HASH + COLON + originalHash + LINE_BREAK
                + prefix + ARCH_SIZE + COLON + archivedSizeBytes + LINE_BREAK
                + prefix + ARCH_HASH + COLON + archivedHash + LINE_BREAK;
    }

    /**
     * Parses the content of the properties into a {@link BarjCargoEntryBoundaries} object.
     *
     * @param properties The properties source
     * @param prefix     The prefix for the entry boundary saved in the source
     * @return the parsed {@link BarjCargoEntryBoundaries}
     */
    public static BarjCargoEntryBoundaries fromProperties(
            final Properties properties, final String prefix) {
        return BarjCargoEntryBoundaries.builder()
                .chunkRelativeStartIndexInclusive(parseLong(properties.getProperty(prefix + REL_START_IDX)))
                .startChunkName(properties.getProperty(prefix + REL_START_FILE))
                .chunkRelativeEndIndexExclusive(parseLong(properties.getProperty(prefix + REL_END_IDX)))
                .endChunkName(properties.getProperty(prefix + REL_END_FILE))
                .absoluteStartIndexInclusive(parseLong(properties.getProperty(prefix + ABS_START_IDX)))
                .absoluteEndIndexExclusive(parseLong(properties.getProperty(prefix + ABS_END_IDX)))
                .originalSizeBytes(parseLong(properties.getProperty(prefix + ORIG_SIZE)))
                .originalHash(nullable(properties.getProperty(prefix + ORIG_HASH)))
                .archivedSizeBytes(parseLong(properties.getProperty(prefix + ARCH_SIZE)))
                .archivedHash(nullable(properties.getProperty(prefix + ARCH_HASH)))
                .build();
    }

    private static String nullable(final String propertyValue) {
        return Optional.ofNullable(propertyValue)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.equals("null"))
                .orElse(null);
    }
}
