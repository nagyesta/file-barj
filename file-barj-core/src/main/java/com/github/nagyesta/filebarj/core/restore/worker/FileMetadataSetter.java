package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;

/**
 * Allows an easy way to set file metadata values to the file.
 */
public interface FileMetadataSetter {

    /**
     * Sets all metadata of the file.
     *
     * @param metadata The metadata
     */
    void setMetadata(@NonNull FileMetadata metadata);

    /**
     * Sets the initial permissions of the file to ensure that the subsequent operations have
     * sufficient permissions.
     *
     * @param metadata The metadata
     */
    void setInitialPermissions(@NonNull FileMetadata metadata);

    /**
     * Sets the permissions of the file as it is defined by the metadata.
     *
     * @param metadata The metadata
     */
    void setPermissions(@NonNull FileMetadata metadata);

    /**
     * Sets the created, last modified and last accessed timestamps of the file as it is defined by
     * the metadata.
     *
     * @param metadata The metadata
     */
    void setTimestamps(@NonNull FileMetadata metadata);

    /**
     * Sets the POSIX owner and group of the file as it is defined by the metadata (if they are set
     * and the FS and OS supports POSIX.
     *
     * @param metadata The metadata
     */
    void setOwnerAndGroup(@NonNull FileMetadata metadata);

    /**
     * Sets the hidden status of the file as it is defined by the metadata.
     *
     * @param metadata The metadata
     */
    void setHiddenStatus(@NonNull FileMetadata metadata);
}
