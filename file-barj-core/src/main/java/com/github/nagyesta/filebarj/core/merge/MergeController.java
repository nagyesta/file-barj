package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.ManifestId;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoInputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

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
@NotNullByDefault
@Slf4j
public class MergeController {
    private static final List<ProgressStep> PROGRESS_STEPS = List.of(LOAD_MANIFESTS, MERGE, DELETE_OBSOLETE_FILES);
    private final ManifestManager manifestManager;
    private final ManifestId mergedManifest;
    private final SortedSet<ManifestId> selectedManifests;
    private final SortedSet<ManifestId> manifestsToMerge;
    private final @Nullable PrivateKey kek;
    private final Path backupDirectory;
    private final ReentrantLock executionLock = new ReentrantLock();
    private final ProgressTracker progressTracker;
    private final ManifestDatabase manifestDatabase;

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
        this.manifestDatabase = manifestManager.loadAll(this.backupDirectory, mergeParameters.getFileNamePrefix(), kek);
        selectedManifests = filterToSelection(manifestDatabase.getAllManifestIds(),
                mergeParameters.getRangeStartEpochSeconds(), mergeParameters.getRangeEndEpochSeconds());
        log.info("Selected {} manifests", selectedManifests.size());
        manifestsToMerge = keepManifestsSinceLastFullBackupOfTheSelection(selectedManifests);
        mergedManifest = manifestDatabase.createMergedIncrement(manifestsToMerge);
        manifestDatabase.getFileStatistics(mergedManifest).forEach(
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
                selectedManifests.forEach(manifest -> {
                    manifestManager.deleteIncrement(backupDirectory, manifestDatabase.getFileNamePrefix(manifest));
                    progressTracker.recordProgressInSubSteps(DELETE_OBSOLETE_FILES);
                });
            }
            return result;
        } finally {
            executionLock.unlock();
        }
    }

    private BackupIncrementManifest mergeBackupContent() {
        final var lastManifest = manifestsToMerge.last();
        final var totalEntries = manifestDatabase.totalCountOfArchiveEntries(lastManifest);
        final var config = manifestDatabase.getConfiguration(mergedManifest);
        progressTracker.estimateStepSubtotal(MERGE, totalEntries);
        final var outputStreamConfiguration = BarjCargoOutputStreamConfiguration.builder()
                .compressionFunction(config.getCompression()::decorateOutputStream)
                .prefix(manifestDatabase.getFileNamePrefix(mergedManifest))
                .folder(backupDirectory)
                .hashAlgorithm(config.getHashAlgorithm().getAlgorithmName())
                .indexEncryptionKey(Optional.ofNullable(kek)
                        .map(key -> manifestDatabase.getDataIndexDecryptionKey(kek, mergedManifest))
                        .orElse(null))
                .maxFileSizeMebibyte(config.getChunkSizeMebibyte())
                .build();
        try (var output = new BarjCargoArchiverFileOutputStream(outputStreamConfiguration)) {
            createDirectoriesForEachVersion(mergedManifest, output);
            final var manifests = manifestsToMerge.stream().distinct().toList();
            for (final var currentManifest : manifests) {
                mergeContentEntriesFromManifest(currentManifest, mergedManifest, output);
            }
            output.close();
            progressTracker.completeStep(MERGE);
            manifestDatabase.setIndexFileName(mergedManifest, output.getIndexFileWritten().getFileName().toString());
            manifestDatabase.setDataFileNames(mergedManifest, output.getDataFilesWritten().stream().map(Path::getFileName).map(Path::toString).toList());
        } catch (final IOException e) {
            throw new ArchivalException("Failed to merge backup increments.", e);
        }
        return manifestDatabase.get(mergedManifest);
    }

    private SortedSet<ManifestId> filterToSelection(
            final SortedSet<ManifestId> manifests,
            final long rangeStartEpochSeconds,
            final long rangeEndEpochSeconds) {
        if (manifests.stream().noneMatch(manifestId -> manifestId.getStartTimeUtcEpochSeconds() == rangeStartEpochSeconds)) {
            throw new IllegalArgumentException("No manifest found with the provided start time: " + rangeStartEpochSeconds);
        }
        if (manifests.stream().noneMatch(manifestId -> manifestId.getStartTimeUtcEpochSeconds() == rangeEndEpochSeconds)) {
            throw new IllegalArgumentException("No manifest found with the provided end time: " + rangeEndEpochSeconds);
        }
        return manifests.stream()
                .filter(manifestId ->
                        manifestId.getStartTimeUtcEpochSeconds() <= rangeEndEpochSeconds
                        && manifestId.getStartTimeUtcEpochSeconds() >= rangeStartEpochSeconds)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private SortedSet<ManifestId> keepManifestsSinceLastFullBackupOfTheSelection(
            final SortedSet<ManifestId> selected) {
        final SortedSet<ManifestId> result = new TreeSet<>();
        final var inReverseOrder = selected.stream()
                .sorted(Comparator.comparingLong(ManifestId::getStartTimeUtcEpochSeconds).reversed())
                .toList();
        for (final var manifest : inReverseOrder) {
            result.add(manifest);
            if (manifest.getBackupType() == BackupType.FULL) {
                if (manifest.getStartTimeUtcEpochSeconds() > selected.first().getStartTimeUtcEpochSeconds()) {
                    log.warn("Skipping merge for manifests before the latest full backup: {}", manifest.getStartTimeUtcEpochSeconds());
                }
                break;
            }
        }
        return result;
    }

    private void createDirectoriesForEachVersion(
            final ManifestId result, final BarjCargoArchiverFileOutputStream output) {
        result.getVersions().forEach(version -> {
            try {
                output.addDirectoryEntity("/" + version, null);
            } catch (final IOException e) {
                throw new ArchivalException("Failed to add directory entity for version " + version, e);
            }
        });
    }

    private void mergeContentEntriesFromManifest(
            final ManifestId currentManifest,
            final ManifestId result,
            final BarjCargoArchiverFileOutputStream output) throws IOException {
        final var relevantEntries = manifestDatabase.retrieveArchiveEntityPathsFor(currentManifest, result);
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

    private BarjCargoInputStreamConfiguration getStreamConfig(
            final ManifestId currentManifest, @Nullable final PrivateKey kek) {
        final var config = manifestDatabase.getConfiguration(currentManifest);
        final var decryptionKey = Optional.ofNullable(kek)
                .map(key -> manifestDatabase.getDataIndexDecryptionKey(key, currentManifest))
                .orElse(null);
        return BarjCargoInputStreamConfiguration.builder()
                .compressionFunction(config.getCompression()::decorateInputStream)
                .prefix(manifestDatabase.getFileNamePrefix(currentManifest))
                .folder(backupDirectory)
                .hashAlgorithm(config.getHashAlgorithm().getAlgorithmName())
                .indexDecryptionKey(decryptionKey)
                .build();
    }
}
