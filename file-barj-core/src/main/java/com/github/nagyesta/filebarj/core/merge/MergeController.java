package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoInputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.*;

/**
 * Controller implementation for the merge process.
 */
@Slf4j
public class MergeController {
    private static final List<ProgressStep> PROGRESS_STEPS = List.of(LOAD_MANIFESTS, MERGE, DELETE_OBSOLETE_FILES);
    private final ManifestManager manifestManager;
    private final RestoreManifest mergedManifest;
    private final SortedMap<Long, BackupIncrementManifest> selectedManifests;
    private final SortedMap<Integer, BackupIncrementManifest> manifestsToMerge;
    private final PrivateKey kek;
    private final Path backupDirectory;
    private final ReentrantLock executionLock = new ReentrantLock();
    private final ProgressTracker progressTracker;

    /**
     * Creates a new instance and initializes it for the merge.
     *
     * @param mergeParameters The parameters.
     */
    public MergeController(
            final @NonNull MergeParameters mergeParameters) {
        mergeParameters.assertValid();
        this.kek = mergeParameters.getKek();
        this.backupDirectory = mergeParameters.getBackupDirectory();
        this.progressTracker = new ObservableProgressTracker(PROGRESS_STEPS);
        progressTracker.registerListener(mergeParameters.getProgressListener());
        manifestManager = new ManifestManagerImpl(progressTracker);
        log.info("Loading backup manifests for merge from: {}", backupDirectory);
        final var manifests = manifestManager.loadAll(this.backupDirectory, mergeParameters.getFileNamePrefix(), kek);
        selectedManifests = filterToSelection(manifests,
                mergeParameters.getRangeStartEpochSeconds(), mergeParameters.getRangeEndEpochSeconds());
        log.info("Selected {} manifests", selectedManifests.size());
        manifestsToMerge = keepManifestsSinceLastFullBackupOfTheSelection(selectedManifests);
        mergedManifest = manifestManager.mergeForRestore(manifestsToMerge);
        final var filesOfLastManifest = mergedManifest.getFilesOfLastManifest();
        LogUtil.logStatistics(filesOfLastManifest.values(),
                (type, count) -> log.info("Found {} {} items in merged backup", count, type));
    }

    /**
     * Execute the merge. If deleteObsoleteFiles is true, the original manifests and backup files
     * which are no longer needed will be deleted.
     *
     * @param deleteObsoleteFiles whether to delete obsolete files from the backup directory
     * @return the merged manifest
     */
    public BackupIncrementManifest execute(final boolean deleteObsoleteFiles) {
        executionLock.lock();
        try {
            progressTracker.reset();
            progressTracker.skipStep(LOAD_MANIFESTS);
            if (!deleteObsoleteFiles) {
                progressTracker.skipStep(DELETE_OBSOLETE_FILES);
            }
            final var result = mergeBackupContent();
            manifestManager.persist(result, backupDirectory);
            if (deleteObsoleteFiles) {
                log.info("Deleting obsolete files from backup directory: {}", backupDirectory);
                progressTracker.estimateStepSubtotal(DELETE_OBSOLETE_FILES, selectedManifests.size());
                selectedManifests.values().forEach(manifest -> {
                    manifestManager.deleteIncrement(backupDirectory, manifest);
                    progressTracker.recordProgressInSubSteps(DELETE_OBSOLETE_FILES);
                });
            }
            return result;
        } finally {
            executionLock.unlock();
        }
    }

    private @NotNull BackupIncrementManifest mergeBackupContent() {
        final var lastManifest = manifestsToMerge.get(manifestsToMerge.lastKey());
        final var firstManifest = manifestsToMerge.get(manifestsToMerge.firstKey());
        final var result = BackupIncrementManifest.builder()
                .backupType(firstManifest.getBackupType())
                .startTimeUtcEpochSeconds(mergedManifest.getLastStartTimeUtcEpochSeconds())
                .configuration(mergedManifest.getConfiguration())
                .appVersion(new AppVersion())
                .fileNamePrefix(firstManifest.getConfiguration().getFileNamePrefix()
                        + "-" + firstManifest.getStartTimeUtcEpochSeconds()
                        + "-" + lastManifest.getStartTimeUtcEpochSeconds())
                .encryptionKeys(mergedManifest.getEncryptionKeys())
                .operatingSystem(lastManifest.getOperatingSystem())
                .versions(mergedManifest.getVersions())
                .files(mergedManifest.getFilesOfLastManifest())
                .archivedEntries(mergedManifest.getArchivedEntriesOfLastManifest())
                .build();
        final var totalEntries = (long) result.getArchivedEntries().values().size();
        progressTracker.estimateStepSubtotal(MERGE, totalEntries);
        final var outputStreamConfiguration = BarjCargoOutputStreamConfiguration.builder()
                .compressionFunction(result.getConfiguration().getCompression()::decorateOutputStream)
                .prefix(result.getFileNamePrefix())
                .folder(backupDirectory)
                .hashAlgorithm(result.getConfiguration().getHashAlgorithm().getAlgorithmName())
                .indexEncryptionKey(Optional.ofNullable(kek)
                        .map(key -> result.dataIndexDecryptionKey(kek, result.getVersions().first()))
                        .orElse(null))
                .maxFileSizeMebibyte(result.getConfiguration().getChunkSizeMebibyte())
                .build();
        try (var output = new BarjCargoArchiverFileOutputStream(outputStreamConfiguration)) {
            createDirectoriesForEachVersion(result, output);
            final var manifests = manifestsToMerge.values().stream().distinct().toList();
            for (final var currentManifest : manifests) {
                mergeContentEntriesFromManifest(currentManifest, result, output);
            }
            output.close();
            progressTracker.completeStep(MERGE);
            result.setIndexFileName(output.getIndexFileWritten().getFileName().toString());
            result.setDataFileNames(output.getDataFilesWritten().stream().map(Path::getFileName).map(Path::toString).toList());
        } catch (final IOException e) {
            throw new ArchivalException("Failed to merge backup increments.", e);
        }
        return result;
    }

    private @NotNull SortedMap<Long, BackupIncrementManifest> filterToSelection(
            final @NotNull SortedMap<Long, BackupIncrementManifest> manifests,
            final long rangeStartEpochSeconds,
            final long rangeEndEpochSeconds) {
        if (!manifests.containsKey(rangeStartEpochSeconds)) {
            throw new IllegalArgumentException("No manifest found with the provided start time: " + rangeStartEpochSeconds);
        }
        if (!manifests.containsKey(rangeEndEpochSeconds)) {
            throw new IllegalArgumentException("No manifest found with the provided end time: " + rangeEndEpochSeconds);
        }
        return manifests.headMap(rangeEndEpochSeconds + 1).tailMap(rangeStartEpochSeconds);
    }

    private @NotNull SortedMap<Integer, BackupIncrementManifest> keepManifestsSinceLastFullBackupOfTheSelection(
            final @NotNull SortedMap<Long, BackupIncrementManifest> selected) {
        final SortedMap<Integer, BackupIncrementManifest> result = new TreeMap<>();
        final var inReverseOrder = selected.values().stream()
                .sorted(Comparator.comparingLong(BackupIncrementManifest::getStartTimeUtcEpochSeconds).reversed())
                .toList();
        for (final var manifest : inReverseOrder) {
            manifest.getVersions().forEach(version -> result.put(version, manifest));
            if (manifest.getBackupType() == BackupType.FULL) {
                if (manifest.getStartTimeUtcEpochSeconds() > selected.firstKey()) {
                    log.warn("Skipping merge for manifests before the latest full backup: {}", manifest.getStartTimeUtcEpochSeconds());
                }
                break;
            }
        }
        return result;
    }

    private void createDirectoriesForEachVersion(
            final BackupIncrementManifest result, final BarjCargoArchiverFileOutputStream output) {
        result.getVersions().forEach(version -> {
            try {
                output.addDirectoryEntity("/" + version, null);
            } catch (final IOException e) {
                throw new ArchivalException("Failed to add directory entity for version " + version, e);
            }
        });
    }

    private void mergeContentEntriesFromManifest(
            final BackupIncrementManifest currentManifest,
            final BackupIncrementManifest result,
            final BarjCargoArchiverFileOutputStream output) throws IOException {
        final var relevantEntries = filterEntities(currentManifest, result);
        final var inputStreamSource = new BarjCargoArchiveFileInputStreamSource(getStreamConfig(currentManifest, kek));
        try (var iterator = inputStreamSource.getIteratorForScope(relevantEntries)) {
            while (iterator.hasNext()) {
                final var currentEntry = iterator.next();
                if (relevantEntries.contains(currentEntry.getPath())) {
                    output.mergeEntity(currentEntry.getEntityIndex(), currentEntry.getRawContentAndMetadata());
                    if (currentEntry.getFileType() != FileType.DIRECTORY) {
                        progressTracker.recordProgressInSubSteps(MERGE);
                    }
                } else {
                    currentEntry.skipContent();
                    currentEntry.skipMetadata();
                }
            }
        }
    }

    private @NonNull Set<String> filterEntities(
            final BackupIncrementManifest currentManifest,
            final BackupIncrementManifest result) {
        return result.getArchivedEntries().values().stream()
                .map(ArchivedFileMetadata::getArchiveLocation)
                .filter(archiveLocation -> currentManifest.getVersions().contains(archiveLocation.getBackupIncrement()))
                .map(ArchiveEntryLocator::asEntryPath)
                .collect(Collectors.toSet());
    }

    private BarjCargoInputStreamConfiguration getStreamConfig(
            final BackupIncrementManifest currentManifest, final PrivateKey kek) {
        final var decryptionKey = Optional.ofNullable(kek)
                .map(key -> currentManifest.dataIndexDecryptionKey(key, currentManifest.getVersions().first()))
                .orElse(null);
        return BarjCargoInputStreamConfiguration.builder()
                .compressionFunction(currentManifest.getConfiguration().getCompression()::decorateInputStream)
                .prefix(currentManifest.getFileNamePrefix())
                .folder(backupDirectory)
                .hashAlgorithm(currentManifest.getConfiguration().getHashAlgorithm().getAlgorithmName())
                .indexDecryptionKey(decryptionKey)
                .build();
    }
}
