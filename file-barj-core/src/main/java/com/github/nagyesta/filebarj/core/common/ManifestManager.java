package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.RestoreManifest;
import com.github.nagyesta.filebarj.core.model.ValidationRules;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.SortedMap;

/**
 * Responsible for loading and merging manifests of the same backup.
 */
public interface ManifestManager {

    /**
     * Generates a new manifest for the provided jobConfiguration.
     *
     * @param jobConfiguration   the jobConfiguration configuration
     * @param backupTypeOverride the backup type we want to use (can override jobConfiguration configuration)
     * @param nextVersion        the version of the backup increment
     * @return the generated manifest
     */
    BackupIncrementManifest generateManifest(
            @NonNull BackupJobConfiguration jobConfiguration,
            @NonNull BackupType backupTypeOverride,
            int nextVersion);

    /**
     * Persists the provided manifest to the hard drive in two copies (one encrypted that can be
     * moved to a safe location and one unencrypted in the history folder to allow incremental
     * backup jobs to function automatically without knowing the private keys).
     *
     * @param manifest The manifest to persist
     */
    void persist(@NonNull BackupIncrementManifest manifest);

    /**
     * Persists the provided manifest to the hard drive in two copies (one encrypted that can be
     * moved to a safe location and one unencrypted in the history folder to allow incremental
     * backup jobs to function automatically without knowing the private keys).
     * The aforementioned files will be stored relative to the provided backup destination.
     *
     * @param manifest          The manifest to persist
     * @param backupDestination the backup destination
     */
    void persist(
            @NonNull BackupIncrementManifest manifest,
            @NonNull Path backupDestination);

    /**
     * Loads the manifests which belong to the provided backup. Only includes manifests starting
     * with the latest full backup before the provided time stamp.
     *
     * @param destinationDirectory    the directory where the backup files are stored
     * @param fileNamePrefix          the prefix of the backup files
     * @param privateKey              the RSA key we want to use to decrypt the manifests (optional).
     *                                If null, the manifests will not be decrypted.
     * @param latestBeforeEpochMillis defines the time stamp until which the manifests should be
     *                                considered
     * @return the map of loaded manifests keyed by their versions
     */
    SortedMap<Integer, BackupIncrementManifest> load(
            @NonNull Path destinationDirectory,
            @NonNull String fileNamePrefix,
            @Nullable PrivateKey privateKey,
            long latestBeforeEpochMillis);

    /**
     * Loads all manifests which belong to the provided backup. Contains manifests for all
     * increments even if many full backups have been created.
     *
     * @param destinationDirectory the directory where the backup files are stored
     * @param fileNamePrefix       the prefix of the backup files
     * @param privateKey           the RSA key we want to use to decrypt the manifests (optional).
     *                             If null, the manifests will not be decrypted.
     * @return the map of loaded manifests keyed by their timestamps
     */
    SortedMap<Long, BackupIncrementManifest> loadAll(
            @NonNull Path destinationDirectory,
            @NonNull String fileNamePrefix,
            @Nullable PrivateKey privateKey);

    /**
     * Merges the provided manifests for a restore process.
     *
     * @param manifests the manifests to merge
     * @return the merged manifest
     */
    RestoreManifest mergeForRestore(
            @NonNull SortedMap<Integer, BackupIncrementManifest> manifests);

    /**
     * Validates the provided manifest using the provided validation rules.
     *
     * @param manifest  the manifest to validate
     * @param forAction the validation rules to use
     */
    void validate(
            @NonNull BackupIncrementManifest manifest,
            @NonNull Class<? extends ValidationRules> forAction);

    /**
     * Loads the previously created manifests of the provided job from the .history folder.
     *
     * @param job the job configuration
     * @return the manifests which can act as previous increments of the provided job
     */
    SortedMap<Integer, BackupIncrementManifest> loadPreviousManifestsForBackup(BackupJobConfiguration job);
}
