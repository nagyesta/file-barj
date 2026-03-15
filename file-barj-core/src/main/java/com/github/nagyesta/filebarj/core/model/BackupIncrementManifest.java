package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.validation.EmptyBackup;
import com.github.nagyesta.filebarj.core.validation.NotEmptyBackup;
import com.github.nagyesta.filebarj.core.validation.PastOrPresentEpochSeconds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Models the root of the backup increment metadata.
 * <br/><br/>
 * This manifest contains every piece of metadata known about the original files and their archived
 * variants.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@EmptyBackup(groups = ValidationRules.Created.class)
@NotEmptyBackup(groups = ValidationRules.Persisted.class)
public class BackupIncrementManifest extends EncryptionKeyStore {

    /**
     * The JSON property name for the app version.
     */
    public static final String APP_VERSION = "app_version";
    /**
     * The JSON property name for the start time.
     */
    public static final String START_TIME_UTC_EPOCH_SECONDS = "start_time_utc_epoch_seconds";
    /**
     * The JSON property name for the filename prefix.
     */
    public static final String FILE_NAME_PREFIX = "file_name_prefix";
    /**
     * The JSON property name for the job configuration.
     */
    public static final String JOB_CONFIGURATION = "job_configuration";
    /**
     * The JSON property name for the backup versions.
     */
    public static final String BACKUP_VERSIONS = "backup_versions";
    /**
     * The JSON property name for the encryption keys.
     */
    public static final String ENCRYPTION_KEYS = "encryption_keys";
    /**
     * The JSON property name for the backup type.
     */
    public static final String BACKUP_TYPE = "backup_type";
    /**
     * The JSON property name for the OS.
     */
    public static final String OPERATING_SYSTEM = "operating_system";
    /**
     * The JSON property name for the index file name.
     */
    public static final String INDEX_FILE_NAME = "index_file_name";
    /**
     * The JSON property name for the data file names.
     */
    public static final String DATA_FILE_NAMES = "data_file_names";
    /**
     * The JSON property name for the files stored in the backup.
     */
    public static final String FILE_COLLECTION = "files";
    /**
     * The JSON property name for the archive entries stored in the backup.
     */
    public static final String ARCHIVE_ENTRY_COLLECTION = "archive_entries";

    /**
     * The version number of the app that generated the manifest.
     */
    private @Valid
    @NonNull AppVersion appVersion;
    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    @PastOrPresentEpochSeconds
    private @Positive long startTimeUtcEpochSeconds;
    /**
     * The file name prefix used by the backup archives.
     */
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    @NotBlank
    private @NonNull String fileNamePrefix;
    /**
     * The type of the backup.
     */
    private @NonNull BackupType backupType;
    /**
     * The OS of the backup.
     */
    private @NotNull(groups = ValidationRules.Created.class)
    @NotBlank(groups = ValidationRules.Created.class) String operatingSystem;
    /**
     * The snapshot of the backup configuration at the time of backup.
     */
    private @Valid
    @NonNull BackupJobConfiguration configuration;
    /**
     * The data store containing the file and archive entry metadata.
     */
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private @NonNull DataStore dataStore;
    /**
     * The set of file metadata identified during backup.
     */
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private @NotNull FileMetadataSetId files;
    /**
     * The set of archive entries saved during backup.
     */
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private @NotNull ArchivedFileMetadataSetId archivedEntries;
    /**
     * The name of the index file.
     */
    private @Null(groups = ValidationRules.Created.class)
    @NotNull(groups = ValidationRules.Persisted.class)
    @NotBlank(groups = ValidationRules.Persisted.class) String indexFileName;
    /**
     * The names of the data files.
     */
    private @Null(groups = ValidationRules.Created.class)
    @NotNull(groups = ValidationRules.Persisted.class)
    @Size(min = 1, groups = ValidationRules.Persisted.class) List<@NotNull String> dataFileNames;

    public FileMetadataSetId getFiles() {
        dataStore.fileMetadataSetRepository().assertExists(files);
        return files;
    }

    public ArchivedFileMetadataSetId getArchivedEntries() {
        dataStore.archivedFileMetadataSetRepository().assertExists(archivedEntries);
        return archivedEntries;
    }
}
