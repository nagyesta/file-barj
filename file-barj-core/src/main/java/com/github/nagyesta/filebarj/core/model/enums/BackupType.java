package com.github.nagyesta.filebarj.core.model.enums;

/**
 * The type of the backup task.
 */
public enum BackupType {
    /**
     * Saves every file without considering any of the previous state.
     * <br/>
     * Ignores previous backups of the same state. Mandatory after configuration changes.
     */
    FULL,
    /**
     * Saves only the delta identified since the last backup increment.
     * <br/>
     * The previous increment may be either a {@link #FULL} or {@code INCREMENTAL} backup. The
     * current increment will consider only the changes since the last increment in either case.
     */
    INCREMENTAL
}
