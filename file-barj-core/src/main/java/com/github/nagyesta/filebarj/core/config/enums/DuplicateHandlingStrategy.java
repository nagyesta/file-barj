package com.github.nagyesta.filebarj.core.config.enums;

/**
 * Defines the strategy used in case a file is found in more than one place.
 */
public enum DuplicateHandlingStrategy {
    /**
     * Archives each copies as separate entry in the archive.
     * <br/>e.g.,<br/>
     * Each duplicate is added as many times as it is found in the source.
     */
    KEEP_EACH,
    /**
     * Archives one copy for each backup increment.
     * <br/>e.g.,<br/>
     * The second instance of the same file is not added to the current backup increment if it was
     * already saved once. Each duplicate can point to the same archive file.
     */
    KEEP_ONE_PER_INCREMENT,
    /**
     * Archives one copy per any increment of the backup since the last full backup.
     * <br/>e.g.,<br/>
     * The file is not added to the current archive even if the duplicate is found archived in a
     * previous backup version, such as a file was overwritten with a previously archived version
     * of the same file,
     */
    KEEP_ONE_PER_BACKUP
}
