package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.common.*;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.*;
import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingFileOutputStream.MEBIBYTE;

/**
 * Controller implementation for the backup process.
 */
@SuppressWarnings({"checkstyle:TodoComment", "java:S1135"})
@Slf4j
public class BackupController
        extends SingleUseController implements Closeable {

    private static final List<ProgressStep> PROGRESS_STEPS = List.of(LOAD_MANIFESTS, SCAN_FILES, PARSE_METADATA, BACKUP);
    private final FileMetadataParser metadataParser = FileMetadataParserFactory.newInstance();
    private final ManifestManager manifestManager;
    @Getter
    private final BackupIncrementManifest manifest;
    private final SortedMap<Integer, BackupIncrementManifest> previousManifests;
    private final SortedMap<Integer, FileMetadataSetId> previousManifestFiles;
    private final SortedMap<Integer, ArchivedFileMetadataSetId> previousManifestArchives;
    private final SortedMap<Integer, String> previousManifestPrefixes;
    private final FileMetadataSetId filesFound;
    private final FileMetadataSetId backupFileSet;
    private final ArchivedFileMetadataSetId backupArchivedFileSet;
    private boolean readyToUse = true;
    private FileMetadataChangeDetector changeDetector;
    private ForkJoinPool threadPool;
    private final ProgressTracker progressTracker;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param parameters The parameters
     */
    public BackupController(final @NonNull BackupParameters parameters) {
        super(DataStore.newInMemoryInstance());
        this.progressTracker = new ObservableProgressTracker(PROGRESS_STEPS);
        progressTracker.registerListener(parameters.getProgressListener());
        this.manifestManager = new ManifestManagerImpl(dataStore(), progressTracker);
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore().archivedFileMetadataSetRepository();
        this.previousManifests = new TreeMap<>();
        this.previousManifestFiles = new TreeMap<>();
        this.previousManifestArchives = new TreeMap<>();
        this.previousManifestPrefixes = new TreeMap<>();
        this.filesFound = fileMetadataSetRepository.createFileSet();
        this.backupFileSet = fileMetadataSetRepository.createFileSet();
        this.backupArchivedFileSet = archivedFileMetadataSetRepository.createFileSet();
        final var backupType = loadPreviousBackups(parameters);
        this.manifest = manifestManager.generateManifest(parameters.getJob(), backupType, previousManifests.size());
    }

    private BackupType loadPreviousBackups(final @NotNull BackupParameters parameters) {
        try {
            final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
            final var archivedFileMetadataSetRepository = dataStore().archivedFileMetadataSetRepository();
            var backupType = parameters.getJob().getBackupType();
            final var forceFull = parameters.isForceFull();
            if (!forceFull && backupType != BackupType.FULL) {
                this.previousManifests.putAll(manifestManager.loadPreviousManifestsForBackup(parameters.getJob()));
                if (previousManifests.isEmpty()) {
                    backupType = BackupType.FULL;
                } else {
                    //TODO: can we move this processing to the load logic?
                    previousManifests.values().forEach(value -> {
                        final var fileSetId = fileMetadataSetRepository.createFileSet();
                        final var archiveSetId = archivedFileMetadataSetRepository.createFileSet();
                        fileMetadataSetRepository.appendTo(fileSetId, value.getFiles().values());
                        archivedFileMetadataSetRepository.appendTo(archiveSetId, value.getArchivedEntries().values());
                        value.getVersions().forEach(increment -> {
                            previousManifestFiles.put(increment, fileSetId);
                            previousManifestArchives.put(increment, archiveSetId);
                            previousManifestPrefixes.put(increment, value.getFileNamePrefix());
                        });
                    });
                }
            }
            if (forceFull) {
                backupType = BackupType.FULL;
            }
            return backupType;
        } catch (final Exception e) {
            //close all resources to eliminate unwanted side effects
            this.close();
            throw new ArchivalException("Failed to load previous backups.", e);
        }
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
        try (var self = lock()) {
            if (readyToUse) {
                threadPool = new ForkJoinPool(threads);
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
        }
    }

    private void listAffectedFilesFromBackupSources() {
        final var fileSetRepository = dataStore().filePathSetRepository();
        log.info("Listing affected files from {} backup sources", manifest.getConfiguration().getSources().size());
        try (var uniquePathFileSet = listSources(fileSetRepository)) {
            if (fileSetRepository.isEmpty(uniquePathFileSet)) {
                throw new IllegalStateException("No files found in backup sources!");
            }
            progressTracker.completeStep(SCAN_FILES);
            detectCaseInsensitivityIssues(uniquePathFileSet);
            final var totalUniquePaths = fileSetRepository.countAll(uniquePathFileSet);
            log.info("Found {} unique paths in backup sources. Parsing metadata...", totalUniquePaths);
            progressTracker.estimateStepSubtotal(PARSE_METADATA, totalUniquePaths);

            final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
            fileSetRepository.forEach(uniquePathFileSet, threadPool, path -> {
                final var fileMetadata = metadataParser.parse(path.toFile(), manifest.getConfiguration());
                progressTracker.recordProgressInSubSteps(PARSE_METADATA);
                fileMetadataSetRepository.appendTo(filesFound, fileMetadata);
            });
            fileMetadataSetRepository.countsByType(filesFound).forEach(
                    (type, count) -> log.info("Found {} {} items in backup sources.", count, type));
            progressTracker.completeStep(PARSE_METADATA);
        }
    }

    private FilePathSetId listSources(final @NotNull FilePathSetRepository filePathSetRepository) {
        final var resultFileSet = filePathSetRepository.createFileSet();
        try {
            manifest.getConfiguration().getSources()
                    .forEach(source -> {
                        log.info("Listing files from backup source: {}", source);
                        new BackupSourceScanner(filePathSetRepository, source).listMatchingFilePaths(resultFileSet);
                    });
            return resultFileSet;
        } catch (final Exception e) {
            log.error("Error listing files from backup sources", e);
            resultFileSet.close();
            throw new ArchivalException("Error listing files from backup sources", e);
        }
    }

    private void detectCaseInsensitivityIssues(final @NotNull FilePathSetId uniquePathFileSet) {
        final var affected = dataStore().filePathSetRepository().detectCaseInsensitivityIssues(uniquePathFileSet);
        if (!affected.isEmpty()) {
            log.warn(LogUtil.scary("Found some paths which differ only in case! The backup cannot be restored correctly on Windows! "
                    + "The affected files are: {}"), affected);
        }
    }

    private void calculateBackupDelta() {
        try (filesFound) {
            log.info("Calculating backup delta using {} previous backup increments", previousManifestFiles.size());
            final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();

            final var previousFiles = new TreeMap<String, FileMetadataSetId>();
            previousManifestFiles.forEach(
                    (key, value) -> previousFiles.put(previousManifestPrefixes.get(key), value));
            if (!previousManifestFiles.isEmpty()) {
                changeDetector = FileMetadataChangeDetectorFactory
                        .create(manifest.getConfiguration(), fileMetadataSetRepository, previousFiles, PermissionComparisonStrategy.STRICT);
                log.info("Trying to find unchanged files in previous backup increments");
                fileMetadataSetRepository.forEach(filesFound, threadPool, this::findPreviousVersionToReuseOrAddToBackupFileSet);
            } else {
                fileMetadataSetRepository.forEach(filesFound, threadPool,
                        fileMetadata -> fileMetadataSetRepository.appendTo(backupFileSet, fileMetadata));
            }
            if (!fileMetadataSetRepository.isEmpty(backupFileSet)) {
                fileMetadataSetRepository.countsByType(backupFileSet).forEach(
                        (type, count) -> log.info("Found {} matching {} items in previous backup increments.", count, type));
            }
            final var changeStats = fileMetadataSetRepository.countsByStatus(filesFound);
            log.info("Detected changes: {}", changeStats);
        }
    }

    private void executeBackup(final int threads) {
        final var startTimeMillis = System.currentTimeMillis();
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore().archivedFileMetadataSetRepository();
        final var totalBackupSize = fileMetadataSetRepository
                .getOriginalSizeBytes(backupFileSet);
        final var fileCount = fileMetadataSetRepository.countAll(backupFileSet);
        progressTracker.estimateStepSubtotal(BACKUP, totalBackupSize);
        final var totalSize = totalBackupSize / MEBIBYTE;
        log.info("Backing up delta for {} files ({} MiB)", fileCount, totalSize);
        try (var pipeline = getPipeline(threads)) {
            final var config = manifest.getConfiguration();
            final var duplicateStrategy = config.getDuplicateStrategy();
            final var hashAlgorithm = config.getHashAlgorithm();
            fileMetadataSetRepository
                    .forEachDuplicateOf(
                            backupFileSet,
                            Change.allStoringContent(),
                            FileType.allContentSources(),
                            duplicateStrategy,
                            hashAlgorithm,
                            threadPool,
                            batch -> processScope(batch, pipeline));
            final var dataFiles = pipeline.getDataFilesWritten().stream()
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList();
            manifest.setDataFileNames(dataFiles);
            manifest.setIndexFileName(pipeline.getIndexFileWritten().getFileName().toString());

            //TODO: need to find a way to stream this into the manifest file
            //move files from the staging area to the actual manifest
            fileMetadataSetRepository
                    .forEach(backupFileSet, threadPool,
                            metadata -> manifest.getFiles().put(metadata.getId(), metadata));
            //move archives from the staging area to the actual manifest
            archivedFileMetadataSetRepository
                    .forEach(backupArchivedFileSet, threadPool,
                            metadata -> manifest.getArchivedEntries().put(metadata.getId(), metadata));

            final var endTimeMillis = System.currentTimeMillis();
            final var durationMillis = endTimeMillis - startTimeMillis;
            progressTracker.completeStep(BACKUP);
            log.info("Archive write completed. Archive write took: {}", toProcessSummary(durationMillis, totalBackupSize));
        } catch (final Exception e) {
            throw new ArchivalException("Archival process failed.", e);
        } finally {
            IOUtils.closeQuietly(backupFileSet);
            IOUtils.closeQuietly(backupArchivedFileSet);
        }
    }

    private void processScope(
            final @NotNull List<List<FileMetadata>> aBatchOfDuplicates,
            final @NotNull BaseBackupPipeline<?> pipeline) {
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore().archivedFileMetadataSetRepository();
        try {
            final var archived = pipeline.storeEntries(aBatchOfDuplicates);
            archivedFileMetadataSetRepository.appendTo(backupArchivedFileSet, archived);
            aBatchOfDuplicates.stream()
                    .flatMap(Collection::stream)
                    .forEach(metadata -> fileMetadataSetRepository
                            .updateArchiveMetadataId(backupFileSet, metadata.getId(), metadata.getArchiveMetadataId()));
            pipeline.close();
        } catch (final Exception e) {
            throw new ArchivalException("Failed to store files: " + e.getMessage(), e);
        }
    }

    private void findPreviousVersionToReuseOrAddToBackupFileSet(final @NotNull FileMetadata file) {
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        if (file.getFileType() == FileType.DIRECTORY) {
            updateDirectoryChangeStatus(file);
            fileMetadataSetRepository.appendTo(backupFileSet, file);
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
        fileMetadataSetRepository.appendTo(backupFileSet, file);
    }

    private void usePreviousVersionInCurrentIncrement(
            final @NotNull FileMetadata previousVersion,
            final @NotNull FileMetadata file) {
        final var fileMetadataSetRepository = dataStore().fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore().archivedFileMetadataSetRepository();
        final var archiveMetadataId = previousVersion.getArchiveMetadataId();

        final var matchingArchiveMetadata = previousManifests.values()
                .stream()
                .map(BackupIncrementManifest::getVersions)
                .map(SortedSet::last)
                .sorted(Comparator.reverseOrder())
                .map(this.previousManifestArchives::get)
                .map(id -> archivedFileMetadataSetRepository.findByFileMetadataId(id, previousVersion.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(archiveEntry -> archiveEntry.getId().equals(archiveMetadataId))
                .findFirst();
        matchingArchiveMetadata.ifPresent(archiveEntry -> {
            final var copied = archiveEntry.copyArchiveDetails();
            copied.getFiles().add(file.getId());
            archivedFileMetadataSetRepository.appendTo(backupArchivedFileSet, copied);
            file.setArchiveMetadataId(copied.getId());
        });
        fileMetadataSetRepository.appendTo(backupFileSet, file);
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

    @Override
    public void close() {
        super.close();
        this.previousManifestFiles.values().forEach(IOUtils::closeQuietly);
        this.previousManifestArchives.values().forEach(IOUtils::closeQuietly);
        IOUtils.closeQuietly(this.filesFound);
        IOUtils.closeQuietly(this.backupFileSet);
        IOUtils.closeQuietly(this.backupArchivedFileSet);
    }
}
