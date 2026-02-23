package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
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
    @JsonProperty("id")
    private final @NonNull UUID id;
    @JsonProperty("file_system_key")
    private final @Nullable String fileSystemKey;
    /**
     * The absolute path where the file is located.
     */
    @JsonProperty("path")
    private final @Valid @NonNull BackupPath absolutePath;
    /**
     * The hash of the file content using the configured hash algorithm.
     * <br/>
     * {@link com.github.nagyesta.filebarj.core.config.BackupJobConfiguration#getHashAlgorithm()}
     */
    @JsonProperty("original_hash")
    private final @Nullable String originalHash;
    /**
     * The original file size.
     */
    @JsonProperty("original_size")
    private @NotNull
    @PositiveOrZero Long originalSizeBytes;
    /**
     * The last modified time of the file using UTC epoch seconds.
     */
    @JsonProperty("last_modified_utc_epoch_seconds")
    private @NotNull Long lastModifiedUtcEpochSeconds;
    /**
     * The last access time of the file using UTC epoch seconds.
     */
    @JsonProperty("last_accessed_utc_epoch_seconds")
    private @NotNull Long lastAccessedUtcEpochSeconds;
    /**
     * The creation time of the file using UTC epoch seconds.
     */
    @JsonProperty("created_utc_epoch_seconds")
    private @NotNull Long createdUtcEpochSeconds;
    /**
     * The POSIX permissions of the file.
     */
    @JsonProperty("permissions")
    private final @NotNull
    @NotBlank
    @Pattern(regexp = "^([r-][w-][x-]){3}$") String posixPermissions;
    /**
     * The owner of the file.
     */
    @JsonProperty("owner")
    private final @NotNull
    @NotBlank String owner;
    /**
     * The owner group of the file.
     */
    @JsonProperty("group")
    private final @NotNull
    @NotBlank String group;
    /**
     * The file type (file/directory/symbolic link/other).
     */
    @JsonProperty("file_type")
    private final @NonNull FileType fileType;
    /**
     * The hidden status of the file.
     */
    @JsonProperty("hidden")
    private @NotNull Boolean hidden;
    /**
     * The detected change status of the file.
     */
    @JsonProperty("status")
    private @NonNull Change status;
    /**
     * The Id of the archive metadata for the entity storing this file.
     */
    @JsonProperty("archive_metadata_id")
    private @Nullable UUID archiveMetadataId;
    /**
     * An optional error message in case of blocker issues during backup.
     */
    @JsonProperty("error")
    private @Nullable String error;

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
        return fileType.streamContent(absolutePath.toOsPath());
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
    public int compareTo(final @NonNull FileMetadata o) {
        return getAbsolutePath().compareTo(o.getAbsolutePath());
    }
}
