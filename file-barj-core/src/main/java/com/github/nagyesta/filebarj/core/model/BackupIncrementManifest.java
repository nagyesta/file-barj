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

import java.util.*;
import java.util.stream.Collectors;

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
public class BackupIncrementManifest extends EncryptionKeyStore implements ManifestId {
    /**
     * The version number of the app that generated the manifest.
     */
    @JsonProperty("app_version")
    private @Valid
    @NonNull AppVersion appVersion;
    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    @PastOrPresentEpochSeconds
    @JsonProperty("start_time_utc_epoch_seconds")
    private @Positive long startTimeUtcEpochSeconds;
    /**
     * The file name prefix used by the backup archives.
     */
    @FileNamePrefix
    @JsonProperty("file_name_prefix")
    private @NonNull String fileNamePrefix;
    /**
     * The type of the backup.
     */
    @JsonProperty("backup_type")
    private @NonNull BackupType backupType;
    /**
     * The OS of the backup.
     */
    @JsonProperty("operating_system")
    private @NotNull(groups = ValidationRules.Created.class)
    @NotBlank(groups = ValidationRules.Created.class) String operatingSystem;
    /**
     * The snapshot of the backup configuration at the time of backup.
     */
    @JsonProperty("job_configuration")
    private @Valid
    @NonNull BackupJobConfiguration configuration;
    /**
     * The map of matching files identified during backup keyed by Id.
     */
    @JsonProperty("files")
    private @Valid
    @Size(max = 0, groups = ValidationRules.Created.class)
    @Size(min = 1, groups = ValidationRules.Persisted.class) Map<UUID, FileMetadata> files;
    /**
     * The map of archive entries saved during backup keyed by Id.
     */
    @JsonProperty("archive_entries")
    private @Valid
    @Size(max = 0, groups = ValidationRules.Created.class) Map<UUID, ArchivedFileMetadata> archivedEntries;
    /**
     * The name of the index file.
     */
    @JsonProperty("index_file_name")
    private @Null(groups = ValidationRules.Created.class)
    @NotNull(groups = ValidationRules.Persisted.class)
    @NotBlank(groups = ValidationRules.Persisted.class) String indexFileName;
    /**
     * The names of the data files.
     */
    @JsonProperty("data_file_names")
    private @Null(groups = ValidationRules.Created.class)
    @NotNull(groups = ValidationRules.Persisted.class)
    @Size(min = 1, groups = ValidationRules.Persisted.class) List<String> dataFileNames;

    public static BackupIncrementManifest copyOfBaseProperties(
            @NonNull final BackupIncrementManifest manifest) {
        return BackupIncrementManifest.builder()
                .appVersion(manifest.getAppVersion())
                .startTimeUtcEpochSeconds(manifest.getStartTimeUtcEpochSeconds())
                .versions(manifest.getVersions())
                .backupType(manifest.getBackupType())
                .operatingSystem(manifest.getOperatingSystem())
                .configuration(manifest.getConfiguration())
                .fileNamePrefix(manifest.getFileNamePrefix())
                .dataFileNames(manifest.getDataFileNames())
                .indexFileName(manifest.getIndexFileName())
                .encryptionKeys(manifest.getEncryptionKeys())
                .files(new HashMap<>())
                .archivedEntries(new HashMap<>())
                .build();
    }

    public static BackupIncrementManifest mergeBaseProperties(
            @NonNull final SortedSet<BackupIncrementManifest> manifests) {
        final var config = manifests.last().getConfiguration();
        return BackupIncrementManifest.builder()
                .appVersion(findMaxAppVersion(manifests))
                .startTimeUtcEpochSeconds(manifests.last().getStartTimeUtcEpochSeconds())
                .versions(mergeVersions(manifests))
                .backupType(manifests.first().getBackupType())
                .operatingSystem(manifests.last().getOperatingSystem())
                .configuration(config)
                .fileNamePrefix(getNewPrefix(manifests, config))
                .encryptionKeys(mergeEncryptionKeys(manifests))
                .files(new HashMap<>())
                .archivedEntries(new HashMap<>())
                .build();
    }

    private static String getNewPrefix(
            final SortedSet<BackupIncrementManifest> manifests,
            final BackupJobConfiguration config) {
        return config.getFileNamePrefix()
                + "-" + manifests.first().getStartTimeUtcEpochSeconds()
                + "-" + manifests.last().getStartTimeUtcEpochSeconds();
    }

    private static SortedSet<Integer> mergeVersions(
            final SortedSet<BackupIncrementManifest> manifests) {
        return manifests.stream()
                .map(BackupIncrementManifest::getVersions)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static AppVersion findMaxAppVersion(final SortedSet<BackupIncrementManifest> manifests) {
        return manifests.stream()
                .map(BackupIncrementManifest::getAppVersion)
                .max(AppVersion::compareTo)
                .orElse(new AppVersion());
    }

    private static SortedMap<Integer, Map<Integer, String>> mergeEncryptionKeys(
            final SortedSet<BackupIncrementManifest> manifests) {
        final var keys = new TreeMap<Integer, Map<Integer, String>>();
        manifests.stream()
                .map(BackupIncrementManifest::getEncryptionKeys)
                .filter(k -> k != null && !k.isEmpty())
                .forEach(keys::putAll);
        return keys;
    }
}
