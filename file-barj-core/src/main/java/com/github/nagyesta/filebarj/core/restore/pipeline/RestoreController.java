package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.RestoreTask;
import com.github.nagyesta.filebarj.core.inspect.worker.ManifestToSummaryConverter;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.RestoreManifest;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream.MEBIBYTE;

/**
 * Controller implementation for the restore process.
 */
@Slf4j
public class RestoreController {
    private final RestoreManifest manifest;
    private final PrivateKey kek;
    private final Path backupDirectory;
    private final ReentrantLock executionLock = new ReentrantLock();
    private ForkJoinPool threadPool;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param backupDirectory the directory where the backup files are located
     * @param fileNamePrefix  the prefix of the backup file names
     * @param kek             The key encryption key we want to use to decrypt the files (optional).
     *                        If null, no decryption will be performed.
     */
    public RestoreController(
            @NotNull final Path backupDirectory,
            @NotNull final String fileNamePrefix,
            @Nullable final PrivateKey kek) {
        this(backupDirectory, fileNamePrefix, kek, Long.MAX_VALUE);
    }

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param backupDirectory the directory where the backup files are located
     * @param fileNamePrefix  the prefix of the backup file names
     * @param kek             The key encryption key we want to use to decrypt the files (optional).
     *                        If null, no decryption will be performed.
     * @param atPointInTime   the point in time to restore from (inclusive).
     */
    public RestoreController(
            @NonNull final Path backupDirectory,
            @NonNull final String fileNamePrefix,
            @Nullable final PrivateKey kek,
            final long atPointInTime) {
        this.kek = kek;
        this.backupDirectory = backupDirectory;
        final ManifestManager manifestManager = new ManifestManagerImpl();
        log.info("Loading backup manifests for restore from: {}", backupDirectory);
        final var manifests = manifestManager.load(backupDirectory, fileNamePrefix, kek, atPointInTime);
        final var header = new ManifestToSummaryConverter().convertToSummaryString(manifests.get(manifests.lastKey()));
        log.info("Latest backup manifest: {}", header);
        log.info("Merging {} manifests", manifests.size());
        manifest = manifestManager.mergeForRestore(manifests);
        final var filesOfLastManifest = manifest.getFilesOfLastManifest();
        LogUtil.logStatistics(filesOfLastManifest.values(),
                (type, count) -> log.info("Found {} {} items in merged backup", count, type));
    }

    /**
     * Execute the restore to the provided root directory.
     *
     * @param restoreTask the parameters of the task we need to perform when we execute the restore
     */
    public void execute(
            @NonNull final RestoreTask restoreTask) {
        if (restoreTask.getThreads() < 1) {
            throw new IllegalArgumentException("Invalid number of threads: " + restoreTask.getThreads());
        }
        executionLock.lock();
        try {
            this.threadPool = new ForkJoinPool(restoreTask.getThreads());
            final var allEntries = manifest.getFilesOfLastManifestFilteredBy(restoreTask.getPathFilter()).values().stream().toList();
            final var contentSources = manifest.getExistingContentSourceFilesOfLastManifestFilteredBy(restoreTask.getPathFilter());
            final long totalBackupSize = allEntries.stream()
                    .map(FileMetadata::getOriginalSizeBytes)
                    .reduce(0L, Long::sum);
            restoreTask.getRestoreTargets().restoreTargets()
                    .forEach(target -> log.info("Restoring {} to {}", target.backupPath(), target.restorePath()));
            log.info("Starting restore of {} MiB backup content (delta not known yet)", totalBackupSize / MEBIBYTE);
            final var startTimeMillis = System.currentTimeMillis();
            final var pipeline = createRestorePipeline(
                    restoreTask.getRestoreTargets(), restoreTask.isDryRun(), restoreTask.getPermissionComparisonStrategy());
            pipeline.restoreDirectories(allEntries.stream()
                    .filter(metadata -> metadata.getFileType() == FileType.DIRECTORY)
                    .toList());
            pipeline.restoreFiles(contentSources, threadPool);
            pipeline.deleteLeftOverFiles(restoreTask.getIncludedPath(), restoreTask.isDeleteFilesNotInBackup(), threadPool);
            pipeline.finalizePermissions(allEntries, threadPool);
            pipeline.evaluateRestoreSuccess(allEntries, threadPool);
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

    @NotNull
    private RestorePipeline createRestorePipeline(
            @NotNull final RestoreTargets restoreTargets,
            final boolean dryRun,
            @Nullable final PermissionComparisonStrategy permissionStrategy) {
        final RestorePipeline pipeline;
        if (dryRun) {
            pipeline = new DryRunRestorePipeline(manifest, backupDirectory, restoreTargets, kek);
        } else {
            pipeline = new RestorePipeline(manifest, backupDirectory, restoreTargets, kek, permissionStrategy);
        }
        return pipeline;
    }

}
