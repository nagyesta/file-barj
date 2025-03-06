package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.RestoreTask;
import com.github.nagyesta.filebarj.core.inspect.worker.ManifestToSummaryConverter;
import com.github.nagyesta.filebarj.core.model.ManifestId;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.*;
import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream.MEBIBYTE;

/**
 * Controller implementation for the restore process.
 */
@Slf4j
public class RestoreController {
    private static final List<ProgressStep> PROGRESS_STEPS = List.of(
            LOAD_MANIFESTS, RESTORE_DIRECTORIES, PARSE_METADATA, RESTORE_CONTENT,
            VERIFY_CONTENT, RESTORE_METADATA, VERIFY_METADATA, DELETE_OBSOLETE_FILES);
    private final ManifestDatabase manifestDatabase;
    private final ManifestId lastIncrement;
    private final PrivateKey kek;
    private final Path backupDirectory;
    private final ReentrantLock executionLock = new ReentrantLock();
    private ForkJoinPool threadPool;
    private final ProgressTracker progressTracker;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param restoreParameters the parameters.
     */
    @SuppressWarnings("checkstyle:TodoComment")
    public RestoreController(
            final @NonNull RestoreParameters restoreParameters) {
        this.kek = restoreParameters.getKek();
        this.backupDirectory = restoreParameters.getBackupDirectory();
        this.progressTracker = new ObservableProgressTracker(PROGRESS_STEPS);
        progressTracker.registerListener(restoreParameters.getProgressListener());
        final ManifestManager manifestManager = new ManifestManagerImpl(progressTracker);
        log.info("Loading backup manifests for restore from: {}", backupDirectory);
        this.manifestDatabase = manifestManager
                .load(backupDirectory, restoreParameters.getFileNamePrefix(), kek, restoreParameters.getAtPointInTime());
        //TODO: eliminate deprecated call
        this.lastIncrement = manifestDatabase.getAllManifestIds().last();
        final var header = new ManifestToSummaryConverter().convertToSummaryString(manifestDatabase.get(lastIncrement));
        log.info("Latest backup manifest: {}", header);
        log.info("Merging {} manifests", manifestDatabase.getAllManifestIds().size());
        manifestDatabase.getFileStatistics(lastIncrement).forEach(
                (type, count) -> log.info("Found {} {} items in merged backup", count, type));
    }

    /**
     * Execute the restore to the provided root directory.
     *
     * @param restoreTask the parameters of the task we need to perform when we execute the restore
     */
    public void execute(
            final @NonNull RestoreTask restoreTask) {
        if (restoreTask.getThreads() < 1) {
            throw new IllegalArgumentException("Invalid number of threads: " + restoreTask.getThreads());
        }
        executionLock.lock();
        try {
            this.threadPool = new ForkJoinPool(restoreTask.getThreads());
            progressTracker.reset();
            progressTracker.skipStep(LOAD_MANIFESTS);
            if (!restoreTask.isDeleteFilesNotInBackup()) {
                progressTracker.skipStep(DELETE_OBSOLETE_FILES);
            }
            final var totalBackupSize = manifestDatabase.originalSizeOfFilesFilteredBy(lastIncrement, restoreTask.getIncludedPath());
            restoreTask.getRestoreTargets().restoreTargets()
                    .forEach(target -> log.info("Restoring {} to {}", target.backupPath(), target.restorePath()));
            log.info("Starting restore of {} MiB backup content (delta not known yet)", totalBackupSize / MEBIBYTE);
            final var startTimeMillis = System.currentTimeMillis();
            final var pipeline = createRestorePipeline(
                    restoreTask.getRestoreTargets(), restoreTask.isDryRun(), restoreTask.getPermissionComparisonStrategy());
            pipeline.setProgressTracker(progressTracker);
            pipeline.restoreDirectories(manifestDatabase
                    .retrieveFilesFilteredBy(lastIncrement, restoreTask.getIncludedPath(), FileType.DIRECTORY.only()));
            pipeline.restoreFiles(manifestDatabase
                    .retrieveFilesFilteredBy(lastIncrement, restoreTask.getIncludedPath(), FileType.contentSources()), threadPool);
            pipeline.deleteLeftOverFiles(restoreTask.getIncludedPath(), restoreTask.isDeleteFilesNotInBackup(), threadPool);
            pipeline.finalizePermissions(manifestDatabase
                    .retrieveFilesFilteredBy(lastIncrement, restoreTask.getIncludedPath(), FileType.allTypes()), threadPool);
            pipeline.evaluateRestoreSuccess(manifestDatabase
                    .retrieveFilesFilteredBy(lastIncrement, restoreTask.getIncludedPath(), FileType.allTypes()), threadPool);
            final var endTimeMillis = System.currentTimeMillis();
            final var durationMillis = (endTimeMillis - startTimeMillis);
            log.info("Restore completed. File operations took: {}", toProcessSummary(durationMillis));
        } finally {
            if (threadPool != null) {
                threadPool.shutdownNow();
                threadPool = null;
            }
            executionLock.unlock();
        }
    }

    private @NotNull RestorePipeline createRestorePipeline(
            final @NotNull RestoreTargets restoreTargets,
            final boolean dryRun,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        final RestorePipeline pipeline;
        if (dryRun) {
            pipeline = new DryRunRestorePipeline(manifestDatabase, lastIncrement, backupDirectory, restoreTargets, kek);
        } else {
            pipeline = new RestorePipeline(manifestDatabase, lastIncrement, backupDirectory, restoreTargets, kek, permissionStrategy);
        }
        return pipeline;
    }
}
