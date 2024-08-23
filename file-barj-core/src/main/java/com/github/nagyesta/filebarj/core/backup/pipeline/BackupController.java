package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.DefaultBackupScopePartitioner;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.common.*;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
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
    private static final int BATCH_SIZE = 250000;
    private static final List<ProgressStep> PROGRESS_STEPS = List.of(LOAD_MANIFESTS, SCAN_FILES, PARSE_METADATA, BACKUP);
    private final FileMetadataParser metadataParser = FileMetadataParserFactory.newInstance();
    private final ManifestManager manifestManager;
    @Getter
    private final BackupIncrementManifest manifest;
    private final SortedMap<Integer, BackupIncrementManifest> previousManifests;
    private final Map<BackupPath, FileMetadata> backupFileSet = new TreeMap<>();
    private List<FileMetadata> filesFound;
    private boolean readyToUse = true;
    private final ReentrantLock executionLock = new ReentrantLock();
    private FileMetadataChangeDetector changeDetector;
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

        final var job = parameters.getJob();
        var backupType = job.getBackupType();
        this.previousManifests = new TreeMap<>();
        final var forceFull = parameters.isForceFull();
        if (!forceFull && backupType != BackupType.FULL) {
            this.previousManifests.putAll(manifestManager.loadPreviousManifestsForBackup(job));
            if (previousManifests.isEmpty()) {
                backupType = BackupType.FULL;
            }
        }
        if (forceFull) {
            backupType = BackupType.FULL;
        }
        this.manifest = manifestManager.generateManifest(job, backupType, previousManifests.size());
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
                listAffectedFilesFromBackupSources();
                calculateBackupDelta();
                executeBackup(threads);
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

    private void listAffectedFilesFromBackupSources() {
        log.info("Listing affected files from {} backup sources", manifest.getConfiguration().getSources().size());
        final SortedSet<Path> uniquePaths = manifest.getConfiguration().getSources().stream()
                .flatMap(source -> {
                    log.info("Listing files from backup source: {}", source);
                    return source.listMatchingFilePaths().stream();
                })
                .collect(Collectors.toCollection(TreeSet::new));
        if (uniquePaths.isEmpty()) {
            throw new IllegalStateException("No files found in backup sources!");
        }
        progressTracker.completeStep(SCAN_FILES);
        detectCaseInsensitivityIssues(uniquePaths);
        log.info("Found {} unique paths in backup sources. Parsing metadata...", uniquePaths.size());
        progressTracker.estimateStepSubtotal(PARSE_METADATA, uniquePaths.size());
        this.filesFound = threadPool.submit(() -> uniquePaths.parallelStream()
                .map(path -> {
                    final var fileMetadata = metadataParser.parse(path.toFile(), manifest.getConfiguration());
                    progressTracker.recordProgressInSubSteps(PARSE_METADATA);
                    return fileMetadata;
                })
                .collect(Collectors.toList())).join();
        LogUtil.logStatistics(filesFound,
                (type, count) -> log.info("Found {} {} items in backup sources.", count, type));
        progressTracker.completeStep(PARSE_METADATA);
    }

    private void detectCaseInsensitivityIssues(final SortedSet<Path> uniquePaths) {
        final var list = uniquePaths.stream()
                .collect(Collectors.groupingBy(path -> path.toString().toLowerCase()))
                .values().stream()
                .filter(paths -> paths.size() > 1)
                .toList();
        if (!list.isEmpty()) {
            log.warn(LogUtil.scary("Found some paths which differ only in case! The backup cannot be restored correctly on Windows! "
                    + "The affected files are: {}"), list);
        }
    }

    private void calculateBackupDelta() {
        log.info("Calculating backup delta using {} previous backup increments", previousManifests.size());
        final var previousFiles = new TreeMap<String, Map<UUID, FileMetadata>>();
        previousManifests.forEach((key, value) -> previousFiles.put(value.getFileNamePrefix(), value.getFiles()));
        if (!previousManifests.isEmpty()) {
            changeDetector = FileMetadataChangeDetectorFactory
                    .create(manifest.getConfiguration(), previousFiles, PermissionComparisonStrategy.STRICT);
            log.info("Trying to find unchanged files in previous backup increments");
            threadPool.submit(() -> this.filesFound.parallelStream()
                    .forEach(this::findPreviousVersionToReuseOrAddToBackupFileSet)).join();
        } else {
            this.filesFound.forEach(file -> backupFileSet.put(file.getAbsolutePath(), file));
        }
        if (!manifest.getFiles().isEmpty()) {
            LogUtil.logStatistics(manifest.getFiles().values(),
                    (type, count) -> log.info("Found {} matching {} items in previous backup increments.", count, type));
        }
        final var changeStats = filesFound.stream()
                .collect(Collectors.groupingBy(FileMetadata::getStatus, Collectors.counting()));
        log.info("Detected changes: {}", changeStats);
        filesFound = null;
    }

    private void usePreviousVersionInCurrentIncrement(final FileMetadata previousVersion, final FileMetadata file) {
        final var archiveMetadataId = previousVersion.getArchiveMetadataId();
        previousManifests.values().stream()
                .sorted(Comparator.comparing(BackupIncrementManifest::getStartTimeUtcEpochSeconds).reversed())
                .map(previousManifest -> Optional.of(previousManifest.getArchivedEntries())
                        .filter(entries -> entries.containsKey(archiveMetadataId))
                        .map(entries -> entries.get(archiveMetadataId))
                        .filter(entry -> entry.getFiles().contains(previousVersion.getId()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(archiveEntry -> {
                    final var copied = archiveEntry.copyArchiveDetails();
                    copied.getFiles().add(file.getId());
                    manifest.getArchivedEntries().put(copied.getId(), copied);
                    file.setArchiveMetadataId(copied.getId());
                });
        manifest.getFiles().put(file.getId(), file);
    }

    private void executeBackup(final int threads) {
        final var startTimeMillis = System.currentTimeMillis();
        final var totalBackupSize = backupFileSet.values().stream()
                .mapToLong(FileMetadata::getOriginalSizeBytes)
                .sum();
        progressTracker.estimateStepSubtotal(BACKUP, totalBackupSize);
        final var totalSize = totalBackupSize / MEBIBYTE;
        log.info("Backing up delta for {} files ({} MiB)", backupFileSet.size(), totalSize);
        try (var pipeline = getPipeline(threads)) {
            this.backupFileSet.values().stream()
                    .filter(metadata -> metadata.getStatus().isStoreContent())
                    .filter(metadata -> metadata.getFileType() == FileType.DIRECTORY)
                    .forEach(metadata -> manifest.getFiles().put(metadata.getId(), metadata));
            final var config = manifest.getConfiguration();
            final var duplicateStrategy = config.getDuplicateStrategy();
            final var hashAlgorithm = config.getHashAlgorithm();
            final var scope = new DefaultBackupScopePartitioner(BATCH_SIZE, duplicateStrategy, hashAlgorithm)
                    .partitionBackupScope(backupFileSet.values());
            try {
                for (final var batch : scope) {
                    final var archived = pipeline.storeEntries(batch);
                    archived.forEach(entry -> manifest.getArchivedEntries().put(entry.getId(), entry));
                }
                scope.stream()
                        .flatMap(Collection::stream)
                        .flatMap(Collection::stream)
                        .forEach(metadata -> manifest.getFiles().put(metadata.getId(), metadata));
            } catch (final Exception e) {
                throw new ArchivalException("Failed to store files: " + e.getMessage(), e);
            }
            pipeline.close();
            final var dataFiles = pipeline.getDataFilesWritten().stream()
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList();
            manifest.setDataFileNames(dataFiles);
            manifest.setIndexFileName(pipeline.getIndexFileWritten().getFileName().toString());
            final var endTimeMillis = System.currentTimeMillis();
            final var durationMillis = endTimeMillis - startTimeMillis;
            progressTracker.completeStep(BACKUP);
            log.info("Archive write completed. Archive write took: {}", toProcessSummary(durationMillis, totalBackupSize));
        } catch (final Exception e) {
            throw new ArchivalException("Archival process failed.", e);
        }
    }

    private void findPreviousVersionToReuseOrAddToBackupFileSet(final @NotNull FileMetadata file) {
        if (file.getFileType() == FileType.DIRECTORY) {
            updateDirectoryChangeStatus(file);
            manifest.getFiles().put(file.getId(), file);
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
        backupFileSet.put(file.getAbsolutePath(), file);
    }

    private void updateDirectoryChangeStatus(final @NotNull FileMetadata file) {
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
            pipeline = new BackupPipeline(manifest);
        } else {
            pipeline = new ParallelBackupPipeline(manifest, threads);
        }
        pipeline.setProgressTracker(progressTracker);
        return pipeline;
    }

    private void saveManifest() {
        manifestManager.persist(manifest);
    }

}
