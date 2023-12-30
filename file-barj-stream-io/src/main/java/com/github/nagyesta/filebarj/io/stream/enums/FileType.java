package com.github.nagyesta.filebarj.io.stream.enums;

import lombok.Getter;

/**
 * Represents the file types we can store in the archive.
 */
@Getter
public enum FileType {
    /**
     * Regular file.
     */
    REGULAR_FILE(EntityArchivalStage.PRE_CONTENT),
    /**
     * Directory.
     */
    DIRECTORY(EntityArchivalStage.PRE_METADATA),
    /**
     * Symbolic link.
     */
    SYMBOLIC_LINK(EntityArchivalStage.PRE_CONTENT);

    private final EntityArchivalStage startStage;

    /**
     * Creates a new instance and sets the starting stage as well.
     *
     * @param startStage The first stage of the archival for the type.
     */
    FileType(final EntityArchivalStage startStage) {
        this.startStage = startStage;
    }
}
