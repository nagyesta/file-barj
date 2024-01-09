package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Contains information about a file from the scope of the backup
 * increment.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileMetadata implements Comparable<FileMetadata> {
    /**
     * The unique Id of the file.
     */
    @NonNull
    @JsonProperty("id")
    private final UUID id;
    @JsonProperty("file_system_key")
    private final String fileSystemKey;
    /**
     * The absolute path where the file is located.
     */
    @NonNull
    @JsonProperty("path")
    private final Path absolutePath;
    /**
     * The hash of the file content using the configured hash algorithm.
     * <br/>
     * {@link com.github.nagyesta.filebarj.core.config.BackupJobConfiguration#getHashAlgorithm()}
     */
    @JsonProperty("original_hash")
    private final String originalHash;
    /**
     * The original file size.
     */
    @JsonProperty("original_size")
    private Long originalSizeBytes;
    /**
     * The last modified time of the file using UTC epoch seconds.
     */
    @JsonProperty("last_modified_utc_epoch_seconds")
    private Long lastModifiedUtcEpochSeconds;
    /**
     * The last access time of the file using UTC epoch seconds.
     */
    @JsonProperty("last_accessed_utc_epoch_seconds")
    private Long lastAccessedUtcEpochSeconds;
    /**
     * The creation time of the file using UTC epoch seconds.
     */
    @JsonProperty("created_utc_epoch_seconds")
    private Long createdUtcEpochSeconds;
    /**
     * The POSIX permissions of the file.
     */
    @JsonProperty("permissions")
    private final String posixPermissions;
    /**
     * The owner of the file.
     */
    @JsonProperty("owner")
    private final String owner;
    /**
     * The owner group of the file.
     */
    @JsonProperty("group")
    private final String group;
    /**
     * The file type (file/directory/symbolic link/other).
     */
    @NonNull
    @JsonProperty("file_type")
    private final FileType fileType;
    /**
     * The hidden status of the file.
     */
    @JsonProperty("hidden")
    private Boolean hidden;
    /**
     * The detected change status of the file.
     */
    @NonNull
    @JsonProperty("status")
    private Change status;
    /**
     * The Id of the archive metadata for the entity storing this file.
     */
    @JsonProperty("archive_metadata_id")
    private UUID archiveMetadataId;
    /**
     * An optional error message in case of blocker issues during backup.
     */
    @JsonProperty("error")
    private String error;

    /**
     * Streams the content of the file. Verifies that the {@link #fileType} is supported by calling
     * {@link #assertContentSource()}.
     *
     * @return input stream with the content of the file
     * @throws IOException When the stream cannot be created
     */
    @JsonIgnore
    public InputStream streamContent() throws IOException {
        assertContentSource();
        return fileType.streamContent(absolutePath);
    }

    /**
     * Calls {@link FileType#isContentSource()} to verify that the file type is a content source.
     *
     * @throws UnsupportedOperationException When the file type is not a  content source
     */
    @JsonIgnore
    public void assertContentSource() {
        if (!fileType.isContentSource()) {
            throw new UnsupportedOperationException(
                    "The provided file (" + absolutePath + ") is not a content source: " + fileType);
        }
    }

    @Override
    public int compareTo(@NonNull final FileMetadata o) {
        return getAbsolutePath().compareTo(o.getAbsolutePath());
    }
}
