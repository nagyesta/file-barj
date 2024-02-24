package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoInputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Controller implementation for the merge process.
 */
@Slf4j
public class MergeController {
    private final ManifestManager manifestManager;
    private final RestoreManifest mergedManifest;
    private final SortedMap<Long, BackupIncrementManifest> selectedManifests;
    private final SortedMap<Integer, BackupIncrementManifest> manifestsToMerge;
    private final PrivateKey kek;
    private final Path backupDirectory;
    private final ReentrantLock executionLock = new ReentrantLock();

    /**
     * Creates a new instance and initializes it for the merge.
     *
     * @param backupDirectory        the directory where the backup files are located
     * @param fileNamePrefix         the prefix of the backup file names
     * @param kek                    The key encryption key we want to use to decrypt and encrypt
     *                               the files (optional).
     * @param rangeStartEpochSeconds the start of the range to merge (inclusive)
     * @param rangeEndEpochSeconds   the end of the range to merge (inclusive)
     */
    public MergeController(
            @NonNull final Path backupDirectory,
            @NonNull final String fileNamePrefix,
            @Nullable final PrivateKey kek,
            final long rangeStartEpochSeconds,
            final long rangeEndEpochSeconds) {
        if (rangeEndEpochSeconds <= rangeStartEpochSeconds) {
            throw new IllegalArgumentException(
                    "Invalid range selected for merge! start=" + rangeEndEpochSeconds + ", end=" + rangeStartEpochSeconds);
        }
        this.kek = kek;
        this.backupDirectory = backupDirectory;
        manifestManager = new ManifestManagerImpl();
        log.info("Loading backup manifests for merge from: {}", backupDirectory);
        final var manifests = manifestManager.loadAll(this.backupDirectory, fileNamePrefix, kek);
        selectedManifests = filterToSelection(manifests, rangeStartEpochSeconds, rangeEndEpochSeconds);
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
            final var result = mergeBackupContent();
            manifestManager.persist(result, backupDirectory);
            if (deleteObsoleteFiles) {
                log.info("Deleting obsolete files from backup directory: {}", backupDirectory);
                selectedManifests.values().forEach(manifest -> {
                    final var fileNamePrefix = manifest.getFileNamePrefix();
                    deleteManifestFromHistoryIfExists(fileNamePrefix);
                    deleteManifestAndArchiveFilesFromBackupDirectory(fileNamePrefix);
                });
            }
            return result;
        } finally {
            executionLock.unlock();
        }
    }

    private void deleteManifestAndArchiveFilesFromBackupDirectory(@NotNull final String fileNamePrefix) {
        final var patterns = Set.of(
                "^" + fileNamePrefix + "\\.[0-9]{5}\\.cargo$",
                "^" + fileNamePrefix + "\\.manifest\\.cargo$",
                "^" + fileNamePrefix + "\\.index\\.cargo$"
        );
        try (var list = Files.list(backupDirectory)) {
            final var toDelete = new ArrayList<Path>();
            list.filter(path -> patterns.stream().anyMatch(pattern -> path.getFileName().toString().matches(pattern)))
                    .forEach(toDelete::add);
            for (final var path : toDelete) {
                log.info("Deleting obsolete file: {}", path);
                Files.delete(path);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteManifestFromHistoryIfExists(@NotNull final String fileNamePrefix) {
        final var fromHistory = backupDirectory.resolve(".history")
                .resolve(fileNamePrefix + ".manifest.json.gz");
        try {
            if (Files.exists(fromHistory)) {
                log.info("Deleting obsolete file from history: {}", fromHistory);
                Files.delete(fromHistory);
            }
        } catch (final IOException e) {
            log.error("Could not delete manifest file from history folder: " + fromHistory, e);
        }
    }

    @NotNull
    private BackupIncrementManifest mergeBackupContent() {
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
            result.setIndexFileName(output.getIndexFileWritten().getFileName().toString());
            result.setDataFileNames(output.getDataFilesWritten().stream().map(Path::getFileName).map(Path::toString).toList());
        } catch (final IOException e) {
            throw new ArchivalException("Failed to merge backup increments.", e);
        }
        return result;
    }

    @NotNull
    private SortedMap<Long, BackupIncrementManifest> filterToSelection(
            @NotNull final SortedMap<Long, BackupIncrementManifest> manifests,
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

    @NotNull
    private SortedMap<Integer, BackupIncrementManifest> keepManifestsSinceLastFullBackupOfTheSelection(
            @NotNull final SortedMap<Long, BackupIncrementManifest> selected) {
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
                } else {
                    currentEntry.skipContent();
                    currentEntry.skipMetadata();
                }
            }
        }
    }

    @NonNull
    private Set<String> filterEntities(
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
