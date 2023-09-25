package com.github.nagyesta.filebarj.core.model.enums;

/**
 * Indicates the change status of a file.
 */
public enum Change {
    /**
     * The file was missing from the previous backup, but it exists now.
     */
    NEW,
    /**
     * The file was present in the previous backup and did not change since.
     */
    NO_CHANGE,
    /**
     * The file was present in the previous backup, but it changed since.
     */
    MODIFIED,
    /**
     * The file was present in the previous backup, but it is missing now (probably because it got deleted).
     */
    DELETED
}
