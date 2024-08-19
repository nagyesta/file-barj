package com.github.nagyesta.filebarj.core.progress;

import lombok.Getter;

@Getter
public enum ProgressStep {
    /**
     * Load manifests for the restore, merge or inspect operation.
     */
    LOAD_MANIFESTS("Load manifests", 1, 10),
    /**
     * Scan the source folders for the files we need to work with.
     */
    SCAN_FILES("Scan files", 100, 5),
    /**
     * Parse the files we have found in the input sources.
     */
    PARSE_METADATA("Parse file metadata", 10, 10),
    /**
     * Backup the change set from the source folder.
     */
    BACKUP("Backup", 5, 50),
    /**
     * Restore directories from tue archive.
     */
    RESTORE_DIRECTORIES("Restore directories", 25, 10),
    /**
     * Restore file contents from tue archive.
     */
    RESTORE_CONTENT("Restore content", 5, 50),
    /**
     * Restore file contents from tue archive.
     */
    RESTORE_METADATA("Restore metadata", 25, 10),
    /**
     * Merge archive increments.
     */
    MERGE("Merge increments", 5, 25),
    /**
     * Verify the content of the restored files.
     */
    VERIFY_CONTENT("Content verification", 10, 10),
    /**
     * Verify the metadata of the restored files.
     */
    VERIFY_METADATA("Metadata verification", 10, 10),
    /**
     * Delete files which are no longer needed.
     */
    DELETE_OBSOLETE_FILES("Obsolete file deletion", 25, 1);

    private final String displayName;
    private final int reportFrequencyPercent;
    private final int defaultWeight;

    ProgressStep(final String displayName, final int reportFrequencyPercent, final int defaultWeight) {
        this.displayName = displayName;
        this.reportFrequencyPercent = reportFrequencyPercent;
        this.defaultWeight = defaultWeight;
    }

}
