package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.DefaultBackupScopePartitioner;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserLocal;
import com.github.nagyesta.filebarj.core.common.FileMetadataChangeDetector;
import com.github.nagyesta.filebarj.core.common.FileMetadataChangeDetectorFactory;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.util.FileTypeStatsUtil;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingFileOutputStream.MEBIBYTE;

/**
 * Controller implementation for the backup process.
 */
@SuppressWarnings({"checkstyle:TodoComment"})
@Slf4j
public class BackupController {
    private static final int BATCH_SIZE = 250000;
    private final FileMetadataParser metadataParser = new FileMetadataParserLocal();
    private final ManifestManager manifestManager = new ManifestManagerImpl();
    @Getter
    private final BackupIncrementManifest manifest;
    private final SortedMap<Integer, BackupIncrementManifest> previousManifests;
    private final Map<Path, FileMetadata> backupFileSet = new TreeMap<>();
    private List<FileMetadata> filesFound;
    private boolean readyToUse = true;
    private final ReentrantLock executionLock = new ReentrantLock();
    private FileMetadataChangeDetector changeDetector;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param job       the job configuration
     * @param forceFull whether to force a full backup (overriding the configuration)
     */
    public BackupController(@NonNull final BackupJobConfiguration job, final boolean forceFull) {
        var backupType = job.getBackupType();
        this.previousManifests = new TreeMap<>();
        if (!forceFull && backupType != BackupType.FULL) {
            this.previousManifests.putAll(manifestManager.loadPreviousManifestsForBackup(job));
            if (previousManifests.isEmpty()) {
                backupType = BackupType.FULL;
            }
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
                listAffectedFilesFromBackupSources();
                calculateBackupDelta();
                executeBackup(threads);
                saveManifest();
                readyToUse = false;
            }
        } finally {
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
        log.info("Found {} unique paths in backup sources. Parsing metadata...", uniquePaths.size());
        this.filesFound = uniquePaths.parallelStream()
                .map(path -> metadataParser.parse(path.toFile(), manifest.getConfiguration()))
                .collect(Collectors.toList());
        FileTypeStatsUtil.logStatistics(filesFound,
                (type, count) -> log.info("Found {} {} items in backup sources.", count, type));
    }

    private void calculateBackupDelta() {
        log.info("Calculating backup delta using {} previous backup increments", previousManifests.size());
        final var previousFiles = new TreeMap<String, Map<UUID, FileMetadata>>();
        previousManifests.forEach((key, value) -> previousFiles.put(value.getFileNamePrefix(), value.getFiles()));
        if (!previousManifests.isEmpty()) {
            changeDetector = FileMetadataChangeDetectorFactory.create(manifest.getConfiguration(), previousFiles);
            this.filesFound.parallelStream().forEach(this::findPreviousVersionToReuseOrAddToBackupFileSet);
        } else {
            this.filesFound.forEach(file -> backupFileSet.put(file.getAbsolutePath(), file));
        }
        if (!manifest.getFiles().isEmpty()) {
            FileTypeStatsUtil.logStatistics(manifest.getFiles().values(),
                    (type, count) -> log.info("Found {} matching {} items in previous backup increments.", count, type));
        }
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
            log.info("Archive write completed. Archive write took: {}", toProcessSummary(durationMillis, totalBackupSize));
        } catch (final Exception e) {
            throw new ArchivalException("Archival process failed.", e);
        }
    }

    private void findPreviousVersionToReuseOrAddToBackupFileSet(@NotNull final FileMetadata file) {
        final var previousVersion = changeDetector.findMostRelevantPreviousVersion(file);
        if (previousVersion != null) {
            final var change = changeDetector.classifyChange(previousVersion, file);
            file.setStatus(change);
            if (previousVersion.getFileType().isContentSource() && change.isLinkPreviousContent()) {
                usePreviousVersionInCurrentIncrement(previousVersion, file);
                return;
            } else if (file.getFileType() == FileType.DIRECTORY) {
                manifest.getFiles().put(file.getId(), file);
                return;
            }
        }
        backupFileSet.put(file.getAbsolutePath(), file);
    }

    @NotNull
    private BaseBackupPipeline<? extends BarjCargoArchiverFileOutputStream> getPipeline(
            final int threads) throws IOException {
        if (threads == 1) {
            return new BackupPipeline(manifest);
        } else {
            return new ParallelBackupPipeline(manifest, threads);
        }
    }

    private void saveManifest() {
        manifestManager.persist(manifest);
    }
}
