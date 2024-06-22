package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.validation.FileNamePrefix;
import com.github.nagyesta.filebarj.core.validation.PastOrPresentEpochSeconds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Models the root of the backup increment metadata.
 * <br/><br/>
 * This manifest contains every piece of metadata known about the original files and their archived
 * variants.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Jacksonized
public class BackupIncrementManifest extends EncryptionKeyStore {
    /**
     * The version number of the app that generated the manifest.
     */
    @Valid
    @NonNull
    @JsonProperty("app_version")
    private AppVersion appVersion;
    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    @Positive
    @PastOrPresentEpochSeconds
    @JsonProperty("start_time_utc_epoch_seconds")
    private long startTimeUtcEpochSeconds;
    /**
     * The file name prefix used by the backup archives.
     */
    @NonNull
    @FileNamePrefix
    @JsonProperty("file_name_prefix")
    private String fileNamePrefix;
    /**
     * The type of the backup.
     */
    @NonNull
    @JsonProperty("backup_type")
    private BackupType backupType;
    /**
     * The OS of the backup.
     */
    @NotNull(groups = ValidationRules.Created.class)
    @NotBlank(groups = ValidationRules.Created.class)
    @JsonProperty("operating_system")
    private String operatingSystem;
    /**
     * The snapshot of the backup configuration at the time of backup.
     */
    @Valid
    @NonNull
    @JsonProperty("job_configuration")
    private BackupJobConfiguration configuration;
    /**
     * The map of matching files identified during backup keyed by Id.
     */
    @Valid
    @Size(max = 0, groups = ValidationRules.Created.class)
    @Size(min = 1, groups = ValidationRules.Persisted.class)
    @JsonProperty("files")
    private Map<UUID, FileMetadata> files;
    /**
     * The map of archive entries saved during backup keyed by Id.
     */
    @Valid
    @Size(max = 0, groups = ValidationRules.Created.class)
    @JsonProperty("archive_entries")
    private Map<UUID, ArchivedFileMetadata> archivedEntries;
    /**
     * The name of the index file.
     */
    @Null(groups = ValidationRules.Created.class)
    @NotNull(groups = ValidationRules.Persisted.class)
    @NotBlank(groups = ValidationRules.Persisted.class)
    @JsonProperty("index_file_name")
    private String indexFileName;
    /**
     * The names of the data files.
     */
    @Null(groups = ValidationRules.Created.class)
    @NotNull(groups = ValidationRules.Persisted.class)
    @Size(min = 1, groups = ValidationRules.Persisted.class)
    @JsonProperty("data_file_names")
    private List<String> dataFileNames;
}
