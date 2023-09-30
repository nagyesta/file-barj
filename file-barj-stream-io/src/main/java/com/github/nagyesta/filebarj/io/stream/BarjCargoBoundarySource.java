package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;

/**
 * Provides access to the content and metadata boundaries of an entity that was written to a
 * {@link BarjCargoArchiverFileOutputStream}.
 */
public interface BarjCargoBoundarySource {

    /**
     * The path of the entity.
     *
     * @return the path
     */
    String getPath();

    /**
     * The type of the entity.
     *
     * @return the type
     */
    FileType getFileType();

    /**
     * Returns whether the entity is encrypted.
     *
     * @return true if the entity is encrypted
     */
    boolean isEncrypted();

    /**
     * The boundaries of the entity's content.
     *
     * @return the content boundaries
     */
    BarjCargoEntryBoundaries getContentBoundary();

    /**
     * The boundaries of the entity's metadata.
     *
     * @return the metadata boundaries
     */
    BarjCargoEntryBoundaries getMetadataBoundary();
}
