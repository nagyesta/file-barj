package com.github.nagyesta.filebarj.io.stream.enums;

import lombok.Getter;

/**
 * Represents the different stages the archival needs to go through during archival of an entry.
 */
@Getter
public enum EntityArchivalStage {
    /**
     * Both streams are closed.
     */
    CLOSED(null),
    /**
     * The metadata stream is open, the metadata can be streamed.
     */
    METADATA(CLOSED),
    /**
     * None of the streams is open, metadata stream can be opened.
     */
    PRE_METADATA(METADATA),
    /**
     * The content stream is open, the entry content can be streamed.
     */
    CONTENT(PRE_METADATA),
    /**
     * None of the streams is open, content stream can be opened.
     */
    PRE_CONTENT(CONTENT);

    private final EntityArchivalStage next;

    /**
     * Creates a new instance and sets the next stage.
     *
     * @param next the next stage, null, when the current stage is terminal.
     */
    EntityArchivalStage(final EntityArchivalStage next) {
        this.next = next;
    }

}
