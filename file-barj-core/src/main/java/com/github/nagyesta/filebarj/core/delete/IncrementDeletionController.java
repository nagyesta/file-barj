package com.github.nagyesta.filebarj.core.delete;

import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Comparator;
import java.util.SortedMap;

/**
 * Controller for the backup increment deletion task.
 */
@Slf4j
public class IncrementDeletionController {

    private final SortedMap<Long, BackupIncrementManifest> manifests;
    private final @NonNull Path backupDirectory;
    private final ManifestManager manifestManager;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param backupDirectory the directory where the backup files are located
     * @param fileNamePrefix  the prefix of the backup file names
     * @param kek             The key encryption key we want to use to decrypt the files (optional).
     *                        If null, no decryption will be performed.
     */
    public IncrementDeletionController(
            @NonNull final Path backupDirectory,
            @NonNull final String fileNamePrefix,
            @Nullable final PrivateKey kek) {
        this.manifestManager = new ManifestManagerImpl();
        this.backupDirectory = backupDirectory;
        this.manifests = this.manifestManager.loadAll(this.backupDirectory, fileNamePrefix, kek);
    }

    /**
     * Deletes the incremental backups which were created after the specified time until the next
     * full backup.
     *
     * @param startingWithEpochSeconds the start time of the first deleted increment
     */
    public void deleteIncrementsUntilNextFullBackupAfter(final long startingWithEpochSeconds) {
        final var incrementsStartingWithThreshold = this.manifests.values().stream()
                .sorted(Comparator.comparing(BackupIncrementManifest::getStartTimeUtcEpochSeconds))
                .filter(manifest -> manifest.getStartTimeUtcEpochSeconds() >= startingWithEpochSeconds)
                .toList();
        if (incrementsStartingWithThreshold.isEmpty()) {
            throw new IllegalArgumentException("No backups found after: " + startingWithEpochSeconds);
        }
        if (incrementsStartingWithThreshold.get(0).getStartTimeUtcEpochSeconds() != startingWithEpochSeconds) {
            throw new IllegalArgumentException("Unable to find backup which started at: " + startingWithEpochSeconds);
        }
        for (final var current : incrementsStartingWithThreshold) {
            if (current.getStartTimeUtcEpochSeconds() > startingWithEpochSeconds && current.getBackupType() == BackupType.FULL) {
                break;
            }
            manifestManager.deleteIncrement(backupDirectory, current);
        }
    }
}
