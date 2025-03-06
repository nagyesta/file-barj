package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.DefaultBackupScopePartitioner;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.common.*;
import com.github.nagyesta.filebarj.core.common.database.FileSetId;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.ManifestId;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.*;
import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingFileOutputStream.MEBIBYTE;

/**
 * Controller implementation for the backup process.
 */
@SuppressWarnings({"checkstyle:TodoComment"})
@Slf4j
public class BackupController {
    private static final int PAGE_SIZE = 5000;
    private static final List<ProgressStep> PROGRESS_STEPS = List.of(LOAD_MANIFESTS, SCAN_FILES, PARSE_METADATA, BACKUP);
    private final FileMetadataParser metadataParser = FileMetadataParserFactory.newInstance();
    private final ManifestManager manifestManager;
    private final ManifestId manifest;
    private final BackupJobConfiguration configuration;
    @Getter
    private final ManifestDatabase manifestDatabase;
    private boolean readyToUse = true;
    private final ReentrantLock executionLock = new ReentrantLock();
    private ForkJoinPool threadPool;
    private final ProgressTracker progressTracker;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param parameters The parameters
     */
    public BackupController(final @NonNull BackupParameters parameters) {
        this.progressTracker = new ObservableProgressTracker(PROGRESS_STEPS);
        progressTracker.registerListener(parameters.getProgressListener());
        this.manifestManager = new ManifestManagerImpl(progressTracker);

        var database = ManifestDatabase.newInstance();
        final var job = parameters.getJob();
        var backupType = job.getBackupType();
        final var forceFull = parameters.isForceFull();
        if (!forceFull && backupType != BackupType.FULL) {
            database = manifestManager.loadPreviousManifestsForBackup(job);
            if (database.isEmpty()) {
                backupType = BackupType.FULL;
            }
        }
        if (forceFull) {
            backupType = BackupType.FULL;
        }
        this.manifestDatabase = database;
        final var backupIncrementManifest = manifestManager.generateManifest(job, backupType, manifestDatabase.nextIncrement());
        this.manifest = manifestDatabase.persistIncrement(backupIncrementManifest);
        this.configuration = manifestDatabase.getLatestConfiguration();
    }

    /**
     * Executes the backup process with the specified number of threads.
     *
     * @param threads the number of threads to use for parallel unpacking
     */
    public void execute(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("Number of threads must be greater than 0");
        }
        executionLock.lock();
        try {
            if (readyToUse) {
                this.threadPool = new ForkJoinPool(threads);
                final var fullBackupScope = listAffectedFilesFromBackupSources();
                final var remainingBackupScope = calculateBackupDelta(fullBackupScope);
                executeBackup(remainingBackupScope, threads);
                saveManifest();
                readyToUse = false;
            } else {
                throw new IllegalStateException("Backup already executed. Please create a new controller instance.");
            }
        } finally {
            if (threadPool != null) {
                threadPool.shutdownNow();
                threadPool = null;
            }
            executionLock.unlock();
        }
    }

    private FileSetId listAffectedFilesFromBackupSources() {
        final var backupScope = manifestDatabase.createFileSet();
        log.info("Listing affected files from {} backup sources", configuration.getSources().size());
        configuration.getSources().forEach(source -> {
            log.info("Listing files from backup source: {}", source);
            manifestDatabase.persistFileSetItems(backupScope, source.listMatchingFilePaths());
        });
        if (manifestDatabase.isFileSetEmpty(backupScope)) {
            throw new IllegalStateException("No files found in backup sources!");
        }
        progressTracker.completeStep(SCAN_FILES);
        final var backupItems = manifestDatabase.countFileSetItems(backupScope);
        detectAndLogCaseSensitivityIssues(backupScope);
        log.info("Found {} unique paths in backup sources. Parsing metadata...", backupItems);
        progressTracker.estimateStepSubtotal(PARSE_METADATA, backupItems);
        manifestDatabase.doForEachPageOfPathsOrderedByPath(backupScope, PAGE_SIZE, list -> {
            log.debug("Parsing a page of {} paths", list.size());
            final var parsed = threadPool.submit(() -> list.parallelStream()
                    .map(path -> {
                        final var fileMetadata = metadataParser.parse(path.toFile(), configuration);
                        progressTracker.recordProgressInSubSteps(PARSE_METADATA);
                        return fileMetadata;
                    })
                    .collect(Collectors.toList())).join();
            manifestDatabase.persistParsedFileMetadataItemsForFileSet(backupScope, parsed);
        });
        manifestDatabase.getFileMetadataStatsForFileSet(backupScope).forEach(
                (type, count) -> log.info("Found {} {} items in backup sources.", count, type));
        progressTracker.completeStep(PARSE_METADATA);
        return backupScope;
    }

    private void detectAndLogCaseSensitivityIssues(final FileSetId backupScope) {
        final var issues = manifestDatabase.retrieveFileWithCaseSensitivityIssues(backupScope);
        if (!issues.isEmpty()) {
            log.warn(LogUtil.scary("Found some paths which differ only in case! The backup cannot be restored correctly on Windows! "
                    + "The affected files are: {}"), issues);
        }
    }

    private FileSetId calculateBackupDelta(final FileSetId backupScope) {
        final var changeSetId = manifestDatabase.createChangeSet();
        try {
            log.info("Calculating backup delta using {} previous backup increments", manifestDatabase.size());
            final var doBackupFileSet = manifestDatabase.createFileSet();
            if (!manifestDatabase.isEmpty()) {
                final var changeDetector = FileMetadataChangeDetectorFactory
                        .create(manifestDatabase, PermissionComparisonStrategy.STRICT);
                log.info("Trying to find unchanged files in previous backup increments");
                manifestDatabase.doForEachPageOfFilesOrderedByPath(backupScope, PAGE_SIZE, files -> {
                    log.debug("Detecting change statuses for a page of {} entries.", files.size());
                    final Map<BackupPath, Change> changeStatuses = new ConcurrentHashMap<>();
                    threadPool.submit(() -> files.parallelStream()
                            .forEach(current ->
                                    findPreviousVersionToReuseOrAddToBackupFileSet(changeDetector, doBackupFileSet, current))
                    ).join();
                    manifestDatabase.persistChangeStatuses(changeSetId, changeStatuses);
                });
            } else {
                manifestDatabase.doForEachPageOfFilesOrderedByPath(backupScope, PAGE_SIZE, files -> {
                    files.forEach(file -> addParsedFileToFileSet(doBackupFileSet, file));
                });
            }
            final var statistics = manifestDatabase.getFileStatistics(manifest);
            if (!statistics.isEmpty()) {
                statistics.forEach(
                        (type, count) -> log.info("Found {} matching {} items in previous backup increments.", count, type));
            }
            final var changeStats = manifestDatabase.getChangeStats(changeSetId);
            log.info("Detected changes: {}", changeStats);
            return doBackupFileSet;
        } finally {
            IOUtils.closeQuietly(backupScope);
            IOUtils.closeQuietly(changeSetId);
        }
    }

    private void usePreviousVersionInCurrentIncrement(final FileMetadata previousVersion, final FileMetadata file) {
        Optional.ofNullable(manifestDatabase.retrieveLatestArchiveMetadataByFileMetadataId(previousVersion.getId()))
                .ifPresent(archiveEntry -> {
                    final var copied = archiveEntry.copyArchiveDetails();
                    copied.getFiles().add(file.getId());
                    manifestDatabase.persistArchiveMetadata(manifest, copied);
                });
        manifestDatabase.persistFileMetadata(manifest, file);
    }

    private void executeBackup(
            final @NotNull FileSetId backupFileSet,
            final int threads) {
        try {
            final var startTimeMillis = System.currentTimeMillis();
            final var totalBackupItems = manifestDatabase.countFileSetItems(backupFileSet);
            final var totalBackupSize = manifestDatabase.sumContentSize(backupFileSet);
            progressTracker.estimateStepSubtotal(BACKUP, totalBackupSize);
            final var totalSize = totalBackupSize / MEBIBYTE;
            log.info("Backing up delta for {} files ({} MiB)", totalBackupItems, totalSize);
            try (var pipeline = getPipeline(threads)) {
                final var duplicateStrategy = configuration.getDuplicateStrategy();
                final var hashAlgorithm = configuration.getHashAlgorithm();
                manifestDatabase.doForEachPageOfFilesOrderedByHashAndCloseQuietly(backupFileSet, PAGE_SIZE, files -> {
                    files.stream()
                            .filter(metadata -> metadata.getStatus().isStoreContent())
                            .filter(metadata -> metadata.getFileType() == FileType.DIRECTORY)
                            .forEach(metadata -> manifestDatabase.persistFileMetadata(manifest, metadata));
                    final var scope = new DefaultBackupScopePartitioner(duplicateStrategy, hashAlgorithm)
                            .partitionBackupScope(files);
                    try {
                        //store the file metadata items first
                        scope.stream()
                                .flatMap(Collection::stream)
                                .forEach(metadata -> manifestDatabase.persistFileMetadata(manifest, metadata));
                        //persist the content
                        final var archived = pipeline.storeEntries(scope);
                        //keep track of the archive entries
                        archived.forEach(entry -> manifestDatabase.persistArchiveMetadata(manifest, entry));
                    } catch (final Exception e) {
                        throw new ArchivalException("Failed to store files: " + e.getMessage(), e);
                    }
                });
                pipeline.close();
                final var dataFiles = pipeline.getDataFilesWritten().stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .toList();
                manifestDatabase.setDataFileNames(manifest, dataFiles);
                manifestDatabase.setIndexFileName(manifest, pipeline.getIndexFileWritten().getFileName().toString());
                final var endTimeMillis = System.currentTimeMillis();
                final var durationMillis = endTimeMillis - startTimeMillis;
                progressTracker.completeStep(BACKUP);
                log.info("Archive write completed. Archive write took: {}", toProcessSummary(durationMillis, totalBackupSize));
            } catch (final Exception e) {
                throw new ArchivalException("Archival process failed.", e);
            }
        } finally {
            IOUtils.closeQuietly(backupFileSet);
        }
    }

    private void findPreviousVersionToReuseOrAddToBackupFileSet(
            final @NotNull FileMetadataChangeDetector changeDetector,
            final @NotNull FileSetId doBackupFileSet,
            final @NotNull FileMetadata file) {
        if (file.getFileType() == FileType.DIRECTORY) {
            updateDirectoryChangeStatus(changeDetector, file);
            manifestDatabase.persistFileMetadata(manifest, file);
            return;
        }
        final var previousVersion = changeDetector.findMostRelevantPreviousVersion(file);
        if (previousVersion != null) {
            final var change = changeDetector.classifyChange(previousVersion, file);
            file.setStatus(change);
            if (previousVersion.getFileType().isContentSource() && change.isLinkPreviousContent()) {
                usePreviousVersionInCurrentIncrement(previousVersion, file);
                return;
            }
        }
        addParsedFileToFileSet(doBackupFileSet, file);
    }

    private void addParsedFileToFileSet(
            final @NotNull FileSetId fileSetId,
            final @NotNull FileMetadata file) {
        manifestDatabase.persistFileSetItems(fileSetId, Collections.singleton(file.getAbsolutePath().toOsPath()));
        manifestDatabase.persistParsedFileMetadataItemsForFileSet(fileSetId, Collections.singleton(file));
    }

    private void updateDirectoryChangeStatus(
            final @NotNull FileMetadataChangeDetector changeDetector,
            final @NotNull FileMetadata file) {
        final var previousVersion = changeDetector.findPreviousVersionByAbsolutePath(file.getAbsolutePath());
        if (previousVersion != null) {
            final var change = changeDetector.classifyChange(previousVersion, file);
            file.setStatus(change);
        }
    }

    private @NotNull BaseBackupPipeline<? extends BarjCargoArchiverFileOutputStream> getPipeline(
            final int threads) throws IOException {
        final BaseBackupPipeline<? extends BarjCargoArchiverFileOutputStream> pipeline;
        if (threads == 1) {
            pipeline = new BackupPipeline(manifestDatabase);
        } else {
            pipeline = new ParallelBackupPipeline(manifestDatabase, threads);
        }
        pipeline.setProgressTracker(progressTracker);
        return pipeline;
    }

    private void saveManifest() {
        manifestManager.persist(manifestDatabase.get(manifest));
    }

    @Deprecated //TODO: remove
    public BackupIncrementManifest getManifest() {
        return manifestDatabase.get(manifest);
    }
}
