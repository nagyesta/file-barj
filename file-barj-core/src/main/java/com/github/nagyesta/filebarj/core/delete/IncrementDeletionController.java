package com.github.nagyesta.filebarj.core.delete;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.model.ManifestId;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.DELETE_OBSOLETE_FILES;
import static com.github.nagyesta.filebarj.core.progress.ProgressStep.LOAD_MANIFESTS;

/**
 * Controller for the backup increment deletion task.
 */
@Slf4j
public class IncrementDeletionController {

    private final ManifestDatabase manifestDatabase;
    private final @NonNull Path backupDirectory;
    private final ManifestManager manifestManager;
    private final ProgressTracker progressTracker;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param parameters The parameters.
     */
    public IncrementDeletionController(
            final @NonNull IncrementDeletionParameters parameters) {
        this.progressTracker = new ObservableProgressTracker(List.of(LOAD_MANIFESTS, DELETE_OBSOLETE_FILES));
        progressTracker.registerListener(parameters.getProgressListener());
        this.manifestManager = new ManifestManagerImpl(progressTracker);
        this.backupDirectory = parameters.getBackupDirectory();
        this.manifestDatabase = this.manifestManager.loadAll(this.backupDirectory, parameters.getFileNamePrefix(), parameters.getKek());
    }

    /**
     * Deletes the incremental backups which were created after the specified time until the next
     * full backup.
     *
     * @param startingWithEpochSeconds the start time of the first deleted increment
     */
    public void deleteIncrementsUntilNextFullBackupAfter(final long startingWithEpochSeconds) {
        final var incrementsStartingWithThreshold = this.manifestDatabase.getAllManifestIds().stream()
                .sorted(Comparator.comparing(ManifestId::getStartTimeUtcEpochSeconds))
                .filter(manifest -> manifest.getStartTimeUtcEpochSeconds() >= startingWithEpochSeconds)
                .toList();
        if (incrementsStartingWithThreshold.isEmpty()) {
            throw new IllegalArgumentException("No backups found after: " + startingWithEpochSeconds);
        }
        if (incrementsStartingWithThreshold.get(0).getStartTimeUtcEpochSeconds() != startingWithEpochSeconds) {
            throw new IllegalArgumentException("Unable to find backup which started at: " + startingWithEpochSeconds);
        }
        progressTracker.estimateStepSubtotal(DELETE_OBSOLETE_FILES, incrementsStartingWithThreshold.size());
        for (final var current : incrementsStartingWithThreshold) {
            if (current.getStartTimeUtcEpochSeconds() > startingWithEpochSeconds && current.getBackupType() == BackupType.FULL) {
                break;
            }
            manifestManager.deleteIncrement(backupDirectory, manifestDatabase.getFileNamePrefix(current));
            progressTracker.recordProgressInSubSteps(DELETE_OBSOLETE_FILES);
        }
        progressTracker.completeStep(DELETE_OBSOLETE_FILES);
    }
}
