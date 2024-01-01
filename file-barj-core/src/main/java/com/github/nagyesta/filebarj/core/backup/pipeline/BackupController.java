package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserLocal;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
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
@SuppressWarnings({"checkstyle:TodoComment", "ConstantValue"})
@Slf4j
public class BackupController {
    private static final int BATCH_SIZE = 250000;
    private final FileMetadataParser metadataParser = new FileMetadataParserLocal();
    private final ManifestManager manifestManager = new ManifestManagerImpl();
    @Getter
    private final BackupIncrementManifest manifest;
    private final List<BackupIncrementManifest> previousManifests;
    private final Map<Path, FileMetadata> backupFileSet = new TreeMap<>();
    private List<FileMetadata> filesFound;
    private boolean readyToUse = true;
    private final ReentrantLock executionLock = new ReentrantLock();

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param job       the job configuration
     * @param forceFull whether to force a full backup (overriding the configuration)
     */
    public BackupController(@NonNull final BackupJobConfiguration job, final boolean forceFull) {
        var backupType = job.getBackupType();
        this.previousManifests = new ArrayList<>();
        if (!forceFull && backupType != BackupType.FULL) {
            //TODO: read previous manifests backwards from latest until the last full backup and
            // compare their configuration with the current job configuration. If they match, use
            // the backupType already set and add the previous backups to the list, otherwise
            // throw away the previous manifests and use FULL
            //this.previousManifests.addALL();
            if (previousManifests.isEmpty()) {
                backupType = BackupType.FULL;
            }
        }
        this.manifest = manifestManager.generateManifest(job, backupType, 0);
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
        log.info("Found {} unique files in backup sources. Parsing metadata...", uniquePaths.size());
        this.filesFound = uniquePaths.parallelStream()
                .map(path -> metadataParser.parse(path.toFile(), manifest.getConfiguration()))
                .collect(Collectors.toList());
        log.info("Parsed metadata of {} files in backup sources.", filesFound.size());
    }

    private void calculateBackupDelta() {
        log.info("Calculating backup delta for {} existing files and {} previous backup increments",
                filesFound.size(), previousManifests.size());
        //TODO: calculate delta when the backup is incremental and update the statuses of the
        // files in the file set before inserting them into the map.
        this.filesFound.forEach(file -> backupFileSet.put(file.getAbsolutePath(), file));
        filesFound = null;
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
            final var scope = this.backupFileSet.values().stream()
                    .filter(metadata -> metadata.getStatus().isStoreContent())
                    .filter(metadata -> metadata.getFileType().isContentSource())
                    .toList();
            try {
                for (var i = 0; i < scope.size(); i += BATCH_SIZE) {
                    final var batch = scope.subList(i, Math.min(i + BATCH_SIZE, scope.size()));
                    final var archived = pipeline.storeEntries(batch);
                    archived.forEach(entry -> manifest.getArchivedEntries().put(entry.getId(), entry));
                }
                scope.forEach(metadata -> manifest.getFiles().put(metadata.getId(), metadata));
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
            log.info("Archive write completed. Total time: {}", toProcessSummary(durationMillis, totalBackupSize));
        } catch (final Exception e) {
            throw new ArchivalException("Archival process failed.", e);
        }
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
