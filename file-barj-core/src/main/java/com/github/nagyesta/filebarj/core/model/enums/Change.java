package com.github.nagyesta.filebarj.core.model.enums;

import lombok.Getter;

/**
 * Indicates the change status of a file.
 */
@Getter
public enum Change {
    /**
     * The file was missing from the previous backup, but it exists now.
     */
    NEW(true, false, false, "File not found in backup."),
    /**
     * The file was present in the previous backup and did not change since.
     */
    NO_CHANGE(false, false, false, "Restored."),
    /**
     * The file was present in the previous backup and only the metadata changed.
     */
    METADATA_CHANGED(false, true, false, "Failed to restore file metadata."),
    /**
     * The file was present in the previous backup, but it changed since.
     */
    CONTENT_CHANGED(true, true, true, "Failed to restore file content."),
    /**
     * The file was present in previous backups and was changed to an older version since the last
     * backup.
     */
    ROLLED_BACK(false, true, true, "Failed to restore file content."),
    /**
     * The file was present in the previous backup, but it is missing now (probably because it got
     * deleted).
     */
    DELETED(false, true, true, "Missing, could not be restored.");

    private final boolean storeContent;
    private final boolean restoreMetadata;
    private final boolean restoreContent;
    private final String restoreStatusMessage;

    /**
     * Initializes the change status constants.
     *
     * @param storeContent         true if the content should be stored during backup creation
     * @param restoreMetadata      true if the metadata should be restored during restore
     * @param restoreContent       true if the content should be restored during restore
     * @param restoreStatusMessage the message to show in case change is detected after a restore
     */
    Change(final boolean storeContent,
           final boolean restoreMetadata,
           final boolean restoreContent,
           final String restoreStatusMessage) {
        this.storeContent = storeContent;
        this.restoreMetadata = restoreMetadata;
        this.restoreContent = restoreContent;
        this.restoreStatusMessage = restoreStatusMessage;
    }
}
