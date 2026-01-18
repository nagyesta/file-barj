package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.common.SingleUseController;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.RestoreTask;
import com.github.nagyesta.filebarj.core.inspect.worker.ManifestToSummaryConverter;
import com.github.nagyesta.filebarj.core.model.RestoreManifest;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.*;
import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream.MEBIBYTE;

/**
 * Controller implementation for the restore process.
 * <br>
 * Warning: Each controller is single-use!
 */
@Slf4j
public class RestoreController
        extends SingleUseController {

    private static final List<ProgressStep> PROGRESS_STEPS = List.of(
            LOAD_MANIFESTS, RESTORE_DIRECTORIES, PARSE_METADATA, RESTORE_CONTENT,
            VERIFY_CONTENT, RESTORE_METADATA, VERIFY_METADATA, DELETE_OBSOLETE_FILES);
    private final RestoreManifest manifest;
    private final PrivateKey kek;
    private final Path backupDirectory;
    private ForkJoinPool threadPool;
    private final ProgressTracker progressTracker;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param restoreParameters the parameters.
     */
    public RestoreController(final @NonNull RestoreParameters restoreParameters) {
        super(DataStore.newInMemoryInstance());
        this.kek = restoreParameters.getKek();
        this.backupDirectory = restoreParameters.getBackupDirectory();
        this.progressTracker = new ObservableProgressTracker(PROGRESS_STEPS);
        progressTracker.registerListener(restoreParameters.getProgressListener());
        final ManifestManager manifestManager = new ManifestManagerImpl(dataStore(), progressTracker);
        log.info("Loading backup manifests for restore from: {}", backupDirectory);
        final var manifests = manifestManager
                .load(backupDirectory, restoreParameters.getFileNamePrefix(), kek, restoreParameters.getAtPointInTime());
        final var header = new ManifestToSummaryConverter().convertToSummaryString(manifests.get(manifests.lastKey()));
        log.info("Latest backup manifest: {}", header);
        log.info("Merging {} manifests", manifests.size());
        this.manifest = manifestManager.mergeForRestore(manifests);
        final var filesOfLastManifest = manifest.getFilesOfLastManifest();
        dataStore().fileMetadataSetRepository().countsByType(filesOfLastManifest).forEach(
                (type, count) -> log.info("Found {} {} items in merged backup", count, type));
    }

    /**
     * Execute the restore to the provided root directory.
     *
     * @param restoreTask the parameters of the task we need to perform when we execute the restore
     */
    public void execute(final @NonNull RestoreTask restoreTask) {
        if (restoreTask.getThreads() < 1) {
            throw new IllegalArgumentException("Invalid number of threads: " + restoreTask.getThreads());
        }
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        try (var self = lock();
             var filteredEntries = fileMetadataSetRepository.createFileSet();
             var contentSources = fileMetadataSetRepository.createFileSet();
             var pipeline = createRestorePipeline(
                     restoreTask.getRestoreTargets(), restoreTask.isDryRun(), restoreTask.getPermissionComparisonStrategy())) {
            this.threadPool = new ForkJoinPool(restoreTask.getThreads());
            progressTracker.reset();
            progressTracker.skipStep(LOAD_MANIFESTS);
            if (!restoreTask.isDeleteFilesNotInBackup()) {
                progressTracker.skipStep(DELETE_OBSOLETE_FILES);
            }
            fileMetadataSetRepository.forEach(manifest.getFiles(), threadPool, file -> {
                if (restoreTask.getPathFilter().test(file.getAbsolutePath())) {
                    fileMetadataSetRepository.appendTo(filteredEntries, file);
                    if (file.getFileType().isContentSource()) {
                        fileMetadataSetRepository.appendTo(contentSources, file);
                    }
                }
            });
            final var totalBackupSize = fileMetadataSetRepository.getOriginalSizeBytes(filteredEntries);
            restoreTask.getRestoreTargets().restoreTargets()
                    .forEach(target -> log.info("Restoring {} to {}", target.backupPath(), target.restorePath()));
            log.info("Starting restore of {} MiB backup content (delta not known yet)", totalBackupSize / MEBIBYTE);
            final var startTimeMillis = System.currentTimeMillis();
            pipeline.setProgressTracker(progressTracker);
            pipeline.restoreDirectories(filteredEntries);
            pipeline.restoreFiles(contentSources, threadPool);
            pipeline.deleteLeftOverFiles(restoreTask.getIncludedPath(), restoreTask.isDeleteFilesNotInBackup(), threadPool);
            pipeline.finalizePermissions(filteredEntries, threadPool);
            pipeline.evaluateRestoreSuccess(filteredEntries, threadPool);
            final var endTimeMillis = System.currentTimeMillis();
            final var durationMillis = (endTimeMillis - startTimeMillis);
            log.info("Restore completed. File operations took: {}", toProcessSummary(durationMillis));
        } finally {
            if (threadPool != null) {
                threadPool.shutdownNow();
                threadPool = null;
            }
        }
    }

    private @NotNull RestorePipeline createRestorePipeline(
            final @NotNull RestoreTargets restoreTargets,
            final boolean dryRun,
            final @Nullable PermissionComparisonStrategy permissionStrategy) {
        final RestorePipeline pipeline;
        if (dryRun) {
            pipeline = new DryRunRestorePipeline(dataStore(), manifest, backupDirectory, restoreTargets, kek);
        } else {
            pipeline = new RestorePipeline(dataStore(), manifest, backupDirectory, restoreTargets, kek, permissionStrategy);
        }
        return pipeline;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(manifest);
        super.close();
    }
}
