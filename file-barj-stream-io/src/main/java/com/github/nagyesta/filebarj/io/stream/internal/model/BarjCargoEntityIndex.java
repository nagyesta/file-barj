package com.github.nagyesta.filebarj.io.stream.internal.model;

import com.github.nagyesta.filebarj.io.stream.BarjCargoBoundarySource;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Optional;
import java.util.Properties;

import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.COLON;
import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.LINE_BREAK;
import static com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries.REL_START_IDX;
import static java.lang.Boolean.parseBoolean;

/**
 * Represents the full set of implicitly collected metadata about a single entity inside the BaRJ
 * cargo archive.
 */
@Data
@Builder
public class BarjCargoEntityIndex implements BarjCargoBoundarySource {

    private static final String PATH = ".path";
    private static final String TYPE = ".type";
    private static final String ENCRYPT = ".encrypt";
    private static final String CONTENT = ".content";
    private static final String METADATA = ".metadata";

    private final @NonNull String path;
    private final @NonNull FileType fileType;
    private final boolean encrypted;
    private final BarjCargoEntryBoundaries content;
    private final @NonNull BarjCargoEntryBoundaries metadata;

    /**
     * Returns the first relevant boundaries from the content or metadata part of the entity.
     *
     * @return the content if there is any, otherwise the metadata
     */
    public BarjCargoEntryBoundaries getContentOrElseMetadata() {
        return Optional.ofNullable(content)
                .orElse(metadata);
    }

    /**
     * Generates a partial properties snippet containing the metadata for the entity.
     *
     * @param prefix prefix for the property names
     * @return properties formatted data
     */
    public String toProperties(final String prefix) {
        return prefix + PATH + COLON + path + LINE_BREAK
                + prefix + TYPE + COLON + fileType.name() + LINE_BREAK
                + prefix + ENCRYPT + COLON + encrypted + LINE_BREAK
                + formatProperties(content, prefix + CONTENT)
                + formatProperties(metadata, prefix + METADATA);
    }

    public static BarjCargoEntityIndex fromProperties(
            final Properties properties, final String prefix) {
        //noinspection DataFlowIssue
        return BarjCargoEntityIndex.builder()
                .path(properties.getProperty(prefix + PATH))
                .fileType(FileType.valueOf(properties.getProperty(prefix + TYPE)))
                .encrypted(parseBoolean(properties.getProperty(prefix + ENCRYPT)))
                .content(parseBoundary(properties, prefix + CONTENT))
                .metadata(parseBoundary(properties, prefix + METADATA))
                .build();
    }

    private String formatProperties(final BarjCargoEntryBoundaries value, final String prefix) {
        return Optional.ofNullable(value)
                .map(p -> p.toProperties(prefix))
                .orElse("");
    }

    private static BarjCargoEntryBoundaries parseBoundary(
            final Properties properties, final String prefix) {
        if (properties.containsKey(prefix + REL_START_IDX)) {
            return BarjCargoEntryBoundaries.fromProperties(properties, prefix);
        } else {
            return null;
        }
    }

    @Override
    public BarjCargoEntryBoundaries getContentBoundary() {
        return content;
    }

    @Override
    public BarjCargoEntryBoundaries getMetadataBoundary() {
        return metadata;
    }
}
