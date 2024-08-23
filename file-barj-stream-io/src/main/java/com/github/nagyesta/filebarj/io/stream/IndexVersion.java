package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.index.ArchiveIndexV1;
import com.github.nagyesta.filebarj.io.stream.index.ArchiveIndexV2;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

/**
 * The version of the File Barj index specification.
 */
@Getter
public enum IndexVersion {
    /**
     * The initial version of the File Barj index specification.
     */
    V1("1") {
        @Override
        ReadOnlyArchiveIndex createIndex(final @NotNull Properties properties) {
            return new ArchiveIndexV1(properties);
        }
    },
    /**
     * The 2nd version of the File Barj index specification.
     */
    V2("2") {
        @Override
        ReadOnlyArchiveIndex createIndex(final @NotNull Properties properties) {
            return new ArchiveIndexV2(properties);
        }
    };

    private final String version;

    IndexVersion(final String version) {
        this.version = version;
    }

    public static IndexVersion forVersionString(final String version) {
        for (final var indexVersion : values()) {
            if (indexVersion.version.equals(version)) {
                return indexVersion;
            }
        }
        return V1;
    }

    /**
     * Instantiates a read-only archive index from the given properties.
     *
     * @param properties the properties
     * @return the read-only archive index
     */
    abstract ReadOnlyArchiveIndex createIndex(@NotNull Properties properties);
}
