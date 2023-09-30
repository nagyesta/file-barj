package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserLocal;
import com.github.nagyesta.filebarj.core.common.FileMetadataChangeDetector;
import com.github.nagyesta.filebarj.core.common.FileMetadataChangeDetectorFactory;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.restore.worker.FileMetadataSetter;
import com.github.nagyesta.filebarj.core.restore.worker.FileMetadataSetterLocal;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoInputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.exception.ArchiveIntegrityException;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import com.github.nagyesta.filebarj.io.stream.model.SequentialBarjCargoArchiveEntry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Performs the actual restore process as instructed by the controller.
 */
@Slf4j
public class RestorePipeline {

    private final Map<Integer, BarjCargoArchiveFileInputStreamSource> cache = new ConcurrentHashMap<>();
    private final FileMetadataChangeDetector changeDetector;
    private final Path backupDirectory;
    private final RestoreTargets restoreTargets;
    private final PrivateKey kek;
    private final RestoreManifest manifest;
    private final FileMetadataSetter fileMetadataSetter;
    private final ReentrantLock streamLock = new ReentrantLock();

    /**
     * Creates a new pipeline instance for the specified manifests.
     *
     * @param manifest        the manifest
     * @param backupDirectory the directory where the backup files are located
     * @param restoreTargets  the mappings of the root paths where we would like to restore
     * @param kek             the key encryption key we would like to use to decrypt files
     */
    public RestorePipeline(@NonNull final RestoreManifest manifest,
                           @NonNull final Path backupDirectory,
                           @NonNull final RestoreTargets restoreTargets,
                           @Nullable final PrivateKey kek) {
        if (manifest.getMaximumAppVersion().compareTo(new AppVersion()) > 0) {
            throw new IllegalArgumentException("Manifests were saved with a newer version of the application");
        }
        this.changeDetector = FileMetadataChangeDetectorFactory.create(manifest.getConfiguration(), manifest.getFiles());
        this.manifest = manifest;
        this.backupDirectory = backupDirectory;
        this.restoreTargets = restoreTargets;
        this.kek = kek;
        this.fileMetadataSetter = new FileMetadataSetterLocal(restoreTargets);
    }

    /**
     * Restore the specified directories.
     *
     * @param directories the directories
     */
    public void restoreDirectories(@NonNull final List<FileMetadata> directories) {
        log.info("Restoring {} directories", directories.size());
        directories.stream()
                .filter(metadata -> metadata.getFileType() == FileType.DIRECTORY)
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath))
                .forEachOrdered(this::restoreDirectory);
        log.info("Restored {} directories", directories.size());
    }

    /**
     * Restore the specified files.
     *
     * @param files   the files
     * @param threads the number of threads to use (max)
     */
    @SuppressWarnings("checkstyle:TodoComment")
    public void restoreFiles(
            @NonNull final Map<FileMetadata, ArchivedFileMetadata> files,
            final int threads) {
        log.info("Restoring {} entries", files.size());
        //TODO: Deletions should not be ignored. We need to perform them in a separate step
        final var matchingFiles = keepExistingFilesAndLinks(files);
        final var changeStatus = detectChanges(matchingFiles.keySet());
        final var filesWithContentChanges = matchingFiles.entrySet().stream()
                //always keep the symbolic links in scope as their change status may not be accurate
                //when restoring links referencing other files from the backup scope, and we are
                //restoring the content to a new location instead of the original source directory
                .filter(entry -> entry.getKey().getFileType() == FileType.SYMBOLIC_LINK
                        || changeStatus.get(entry.getKey().getId()).isRestoreContent())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        restoreContent(threads, filesWithContentChanges, matchingFiles.size());
    }

    /**
     * Finalizes the permissions of the restored files.
     *
     * @param files The files in scope
     */
    public void finalizePermissions(@NonNull final List<FileMetadata> files) {
        log.info("Finalizing permissions for {} files", files.size());
        final var changeStatus = detectChanges(files);
        final var filesWithMetadataChanges = files.stream()
                .filter(entry -> changeStatus.get(entry.getId()).isRestoreMetadata())
                .toList();
        filesWithMetadataChanges.stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath).reversed())
                .forEachOrdered(this::setFileProperties);
        log.info("Finalizing permissions was required for {} entries out of {} files",
                filesWithMetadataChanges.size(), files.size());
    }

    /**
     * Checks the file statuses after the restore is completed and reports any files that could not
     * be fully restored (either having the wrong content or the wrong metadata).
     *
     * @param files The files to check
     */
    public void evaluateRestoreSuccess(@NonNull final List<FileMetadata> files) {
        final var checkOutcome = detectChanges(files);
        files.stream()
                //cannot verify symbolic links because they can be referencing files from the
                //restore folder which is not necessarily the same as the original backup folder
                .filter(entry -> entry.getFileType() != FileType.SYMBOLIC_LINK)
                .filter(entry -> checkOutcome.get(entry.getId()).isRestoreMetadata())
                .forEach(file -> {
                    final var change = Optional.ofNullable(checkOutcome.get(file.getId()))
                            .map(Change::getRestoreStatusMessage)
                            .orElse("Unknown.");
                    final var restorePath = restoreTargets.mapToRestorePath(file.getAbsolutePath());
                    log.warn("Could not fully restore file {} (status is: {})", restorePath, change);
                });
    }

    /**
     * Sets the file properties for the specified file based on the file metadata saved during backup.
     *
     * @param fileMetadata the file metadata
     */
    protected void setFileProperties(@NotNull final FileMetadata fileMetadata) {
        fileMetadataSetter.setMetadata(fileMetadata);
    }

    /**
     * Returns the restore targets.
     *
     * @return the restore targets
     */
    protected RestoreTargets getRestoreTargets() {
        return restoreTargets;
    }

    /**
     * Removes the existing file and creates a symbolic link to point ot the desired target..
     *
     * @param linkTarget   the link target
     * @param symbolicLink the link file we need to create
     * @throws IOException if an I/O error occurs
     */
    protected void createSymbolicLink(
            @NotNull final Path linkTarget, @NotNull final Path symbolicLink) throws IOException {
        Files.createSymbolicLink(symbolicLink, linkTarget);
    }

    /**
     * Copies the original file to the remaining locations.
     *
     * @param original        the original file metadata
     * @param remainingCopies the remaining copies we need tp restore
     */
    protected void copyRestoredFileToRemainingLocations(
            @NotNull final FileMetadata original,
            @NotNull final List<FileMetadata> remainingCopies) {
        final var unpackedFile = restoreTargets.mapToRestorePath(original.getAbsolutePath());
        remainingCopies.forEach(file -> {
            final var copy = restoreTargets.mapToRestorePath(file.getAbsolutePath());
            try {
                if (original.getFileSystemKey().equals(file.getFileSystemKey())) {
                    Files.createLink(copy, unpackedFile);
                } else {
                    Files.copy(unpackedFile, copy);
                }
            } catch (final IOException e) {
                throw new ArchivalException("Failed to copy file: " + unpackedFile + " to: " + copy, e);
            }
        });
    }

    /**
     * Creates a directory.
     *
     * @param path the path
     * @throws IOException if an I/O error occurs
     */
    protected void createDirectory(@NotNull final Path path) throws IOException {
        Files.createDirectories(path);
    }

    /**
     * Restores the content of the specified file.
     *
     * @param content the content
     * @param target  the target where we need to store the content
     */
    protected void restoreFileContent(@NotNull final InputStream content, @NotNull final Path target) {
        try (var outputStream = new FileOutputStream(target.toFile());
             var bufferedStream = new BufferedOutputStream(outputStream);
             var countingStream = new CountingOutputStream(bufferedStream)) {
            IOUtils.copy(content, countingStream);
            log.debug("Restored file: {}", target);
        } catch (final IOException e) {
            throw new ArchivalException("Failed to restore content: " + target, e);
        }
    }

    /**
     * Deletes the currently existing file, link or directory (allowing the restore to replace it later).
     *
     * @param currentFile the current file
     * @throws IOException if an I/O error occurs
     */
    protected void deleteIfExists(@NotNull final Path currentFile) throws IOException {
        if (!Files.exists(currentFile)) {
            return;
        }
        if (Files.isDirectory(currentFile)) {
            FileUtils.deleteDirectory(currentFile.toFile());
        } else {
            Files.delete(currentFile);
        }
    }

    private void restoreContent(
            final int threads,
            @NotNull final Map<FileMetadata, ArchivedFileMetadata> filesWithContentChanges,
            final int totalFiles) {
        final var fileIndex = indexFiles(filesWithContentChanges);
        final var linkPaths = new ConcurrentHashMap<FileMetadata, Path>();
        final var scopePerManifest = new ConcurrentHashMap<String, Map<UUID, FileMetadata>>();
        manifest.getFileNamePrefixes()
                .forEach(prefix -> {
                    final var scope = filesWithContentChanges.keySet();
                    final var found = calculateRemainingEntries(manifest, prefix, scope);
                    scopePerManifest.put(prefix, found);
                });
        log.info("Found {} manifests.", scopePerManifest.size());
        final var partitions = scopePerManifest.entrySet().stream()
                .flatMap(entry -> {
                    final var prefix = entry.getKey();
                    final var scope = entry.getValue();
                    final var archiveEntriesInScope = filterArchiveEntriesToFilesInScope(manifest, prefix, scope.values().stream()
                            .map(FileMetadata::getId)
                            .collect(Collectors.toSet()));
                    try {
                        final var matchingEntriesInOrderOfOccurrence = getStreamSource(manifest, prefix)
                                .getMatchingEntriesInOrderOfOccurrence(archiveEntriesInScope);
                        final var chunks = partition(matchingEntriesInOrderOfOccurrence, fileIndex, threads);
                        return chunks.stream().map(chunk -> new AbstractMap.SimpleEntry<>(prefix, chunk));
                    } catch (final IOException e) {
                        throw new ArchivalException("Failed to filter archive entries.", e);
                    }
                })
                .filter(entry -> !entry.getValue().isEmpty())
                .toList();
        log.info("Formed {} partitions", partitions.size());
        partitions.parallelStream().forEach(entry -> {
            final var prefix = entry.getKey();
            final var scope = entry.getValue();
            final var resolvedLinks = restoreMatchingEntriesOfManifest(manifest, prefix, scope, fileIndex);
            linkPaths.putAll(resolvedLinks);
        });
        createSymbolicLinks(linkPaths);
        log.info("Restored {} changed entries of {} files", filesWithContentChanges.size(), totalFiles);
    }

    @NotNull
    private List<Set<FileMetadata>> partition(
            @NotNull final List<BarjCargoEntityIndex> input,
            @NotNull final ConcurrentHashMap<UUID, Map<FileMetadata, ArchivedFileMetadata>> fileIndex,
            final int partitions) {
        final var chunks = new ArrayList<Set<FileMetadata>>();
        final var threshold = (input.size() / partitions) + 1;
        var collector = new HashSet<FileMetadata>(threshold);
        for (final var file : input) {
            final var locator = ArchiveEntryLocator.fromEntryPath(file.getPath());
            if (locator == null) {
                throw new ArchivalException("Failed to parse entry locator for " + file.getPath());
            }
            final var entryName = locator.getEntryName();
            final var current = fileIndex.get(entryName).keySet();
            for (final var entry : current) {
                if (collector.size() >= threshold) {
                    chunks.add(collector);
                    collector = new HashSet<>(threshold);
                }
                collector.add(entry);
            }
        }
        chunks.add(collector);
        return chunks;
    }

    private Map<FileMetadata, Path> restoreMatchingEntriesOfManifest(
            @NotNull final RestoreManifest manifest,
            @NotNull final String prefix,
            @NotNull final Set<FileMetadata> scope,
            @NotNull final Map<UUID, Map<FileMetadata, ArchivedFileMetadata>> fileIndex) {
        final var remaining = calculateRemainingEntries(manifest, prefix, scope);
        log.info("Found {} entries in manifest with versions: {}", remaining.size(), manifest.getVersions());
        final var linkPaths = new HashMap<FileMetadata, Path>();
        try {
            final var streamSource = getStreamSource(manifest, prefix);
            final var archiveEntriesInScope = filterArchiveEntriesToFilesInScope(manifest, prefix, remaining.keySet());
            final var it = streamSource.getIteratorForScope(archiveEntriesInScope);
            while (it.hasNext()) {
                final var archiveEntry = it.next();
                if (remaining.isEmpty()) {
                    break;
                }
                final var locator = ArchiveEntryLocator.fromEntryPath(archiveEntry.getPath());
                if (locator == null) {
                    throw new ArchivalException("Failed to parse entry locator for " + archiveEntry.getPath());
                }
                final var key = getDecryptionKey(manifest, locator);
                if (skipIfNotInScope(fileIndex, archiveEntry)) {
                    continue;
                }
                final var mappingsOfArchiveEntry = fileIndex.get(locator.getEntryName());
                final var entries = mappingsOfArchiveEntry.keySet().stream().toList();
                final var type = getSingleEntryType(mappingsOfArchiveEntry, locator.getEntryName());
                if (Objects.requireNonNull(type) == FileType.REGULAR_FILE) {
                    restoreFileContent(archiveEntry, entries.get(0), key);
                    copyRestoredFileToRemainingLocations(entries.get(0), entries.stream().skip(1).toList());
                    entries.forEach(metadata -> remaining.remove(metadata.getId()));
                    skipMetadata(archiveEntry);
                } else if (type == FileType.SYMBOLIC_LINK) {
                    final var targetPath = resolveLinkTarget(archiveEntry, entries.get(0), key, manifest);
                    entries.forEach(metadata -> {
                        linkPaths.put(metadata, targetPath);
                        remaining.remove(metadata.getId());
                    });
                    skipMetadata(archiveEntry);
                }
            }
            log.info("Processed manifest with versions: {}", manifest.getVersions());
            return linkPaths;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to read source for manifest with versions: " + manifest.getVersions(), e);
        }
    }

    @NotNull
    private static Set<String> filterArchiveEntriesToFilesInScope(
            @NotNull final RestoreManifest manifest,
            @NotNull final String prefix,
            @NotNull final Set<UUID> remainingFileMetadataIds) {
        return manifest.getArchivedEntries().get(prefix).values().stream()
                .filter(archivedFileMetadata -> archivedFileMetadata.getFiles().stream()
                        .anyMatch(remainingFileMetadataIds::contains))
                .map(ArchivedFileMetadata::getArchiveLocation)
                .filter(location -> manifest.getVersions().contains(location.getBackupIncrement()))
                .map(ArchiveEntryLocator::asEntryPath)
                .collect(Collectors.toSet());
    }

    @NotNull
    private Map<FileMetadata, ArchivedFileMetadata> keepExistingFilesAndLinks(
            @NotNull final Map<FileMetadata, ArchivedFileMetadata> files) {
        return files.entrySet().stream()
                .filter(entry -> entry.getKey().getStatus() != Change.DELETED)
                .filter(entry -> entry.getKey().getFileType().isContentSource())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @NotNull
    private Map<UUID, Change> detectChanges(
            @NotNull final Collection<FileMetadata> files) {
        final var parser = new FileMetadataParserLocal();
        final Map<UUID, Change> changeStatuses = new HashMap<>();
        files.forEach(file -> {
            final var previous = changeDetector.findPreviousVersionByAbsolutePath(file.getAbsolutePath());
            if (previous == null) {
                throw new IllegalStateException("Previous version not found for " + file.getAbsolutePath());
            }
            final var restorePath = restoreTargets.mapToRestorePath(file.getAbsolutePath());
            final var current = parser.parse(restorePath.toFile(), manifest.getConfiguration());
            final var change = changeDetector.classifyChange(previous, current);
            changeStatuses.put(file.getId(), change);
        });
        return changeStatuses;
    }

    @NotNull
    private ConcurrentHashMap<UUID, Map<FileMetadata, ArchivedFileMetadata>> indexFiles(
            final Map<FileMetadata, ArchivedFileMetadata> matchingFiles) {
        final var fileIndex = new ConcurrentHashMap<UUID, Map<FileMetadata, ArchivedFileMetadata>>();
        matchingFiles.entrySet().parallelStream()
                .forEach(entry -> {
                    final var file = entry.getKey();
                    final var archive = entry.getValue();
                    final var entryName = archive.getArchiveLocation().getEntryName();
                    final var map = fileIndex.computeIfAbsent(entryName, k -> new ConcurrentHashMap<>());
                    map.put(file, archive);
                });
        return fileIndex;
    }

    private void createSymbolicLinks(@NotNull final ConcurrentHashMap<FileMetadata, Path> linkPaths) {
        linkPaths.forEach((metadata, linkTarget) -> {
            final var symbolicLink = restoreTargets.mapToRestorePath(metadata.getAbsolutePath());
            try {
                if (shouldCreateNewLink(linkTarget, symbolicLink)) {
                    deleteIfExists(symbolicLink);
                    createSymbolicLink(linkTarget, symbolicLink);
                }
            } catch (final IOException e) {
                throw new ArchivalException("Failed to create symbolic link: " + symbolicLink + " pointing to: " + linkTarget, e);
            }
        });
    }

    private boolean shouldCreateNewLink(final Path linkTarget, final Path to) throws IOException {
        var linkNeeded = true;
        if (Files.exists(to) && Files.isSymbolicLink(to)) {
            final var currentTarget = IOUtils.toString(FileType.SYMBOLIC_LINK.streamContent(to), StandardCharsets.UTF_8);
            if (currentTarget.equals(linkTarget.toString())) {
                linkNeeded = false;
            }
        }
        return linkNeeded;
    }

    private boolean skipIfNotInScope(
            @NotNull final Map<UUID, Map<FileMetadata, ArchivedFileMetadata>> fileIndex,
            @NotNull final SequentialBarjCargoArchiveEntry archiveEntry) {
        final var locator = ArchiveEntryLocator.fromEntryPath(archiveEntry.getPath());
        if (locator == null) {
            return true;
        } else if (fileIndex.containsKey(locator.getEntryName())) {
            return false;
        }
        try {
            archiveEntry.skipContent();
            archiveEntry.skipMetadata();
            return true;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to skip content and metadate.", e);
        }
    }

    private FileType getSingleEntryType(
            @NotNull final Map<FileMetadata, ArchivedFileMetadata> mappingsOfArchiveEntry,
            @NotNull final UUID archiveEntryId) {
        final var types = mappingsOfArchiveEntry.keySet().stream()
                .map(FileMetadata::getFileType)
                .distinct()
                .toList();
        if (types.size() > 1) {
            throw new ArchiveIntegrityException("Multiple types found for entry: " + archiveEntryId);
        }
        return types.get(0);
    }

    private void restoreFileContent(
            @NotNull final SequentialBarjCargoArchiveEntry archiveEntry,
            @NotNull final FileMetadata fileMetadata,
            @Nullable final SecretKey key) {
        log.debug("Restoring entry: {} to file: {}", archiveEntry.getPath(), fileMetadata.getAbsolutePath());
        final var unpackTo = restoreTargets.mapToRestorePath(fileMetadata.getAbsolutePath());
        try (var fileContent = archiveEntry.getFileContent(key)) {
            deleteIfExists(unpackTo);
            restoreFileSequentially(fileContent, fileMetadata);
        } catch (final IOException e) {
            throw new ArchivalException("Failed to restore file: " + unpackTo, e);
        }
    }

    @NotNull
    private Path resolveLinkTarget(
            @NotNull final SequentialBarjCargoArchiveEntry archiveEntry,
            @NotNull final FileMetadata fileMetadata,
            @Nullable final SecretKey key,
            @NotNull final RestoreManifest manifest) {
        final var to = restoreTargets.mapToRestorePath(fileMetadata.getAbsolutePath());
        try {
            final var target = archiveEntry.getLinkTarget(key);
            final var pointsToAFileCoveredByTheBackup = manifest.allFilesReadOnly().values().stream()
                    .filter(file -> file.getStatus() != Change.DELETED)
                    .map(FileMetadata::getAbsolutePath)
                    .anyMatch(path -> path.toString().equals(target));
            final var targetPath = Path.of(target);
            return restoreTargets.restoreTargets().stream()
                    .filter(restoreTarget -> pointsToAFileCoveredByTheBackup && restoreTarget.matchesArchivedFile(targetPath))
                    .findFirst()
                    .map(filePath -> filePath.mapBackupPathToRestorePath(targetPath))
                    .orElse(targetPath);
        } catch (final IOException e) {
            throw new ArchivalException("Failed to resolve link path for: " + to, e);
        }
    }

    private void skipMetadata(@NotNull final SequentialBarjCargoArchiveEntry archiveEntry) {
        try {
            archiveEntry.skipMetadata();
        } catch (final IOException e) {
            throw new ArchivalException("Failed to skip metadate.", e);
        }
    }

    @NotNull
    private Map<UUID, FileMetadata> calculateRemainingEntries(
            @NotNull final RestoreManifest manifest,
            @NotNull final String fileNamePrefix,
            @NotNull final Set<FileMetadata> files) {
        return new ConcurrentHashMap<>(files.stream()
                .filter(fileMetadata -> manifest.getFiles().get(fileNamePrefix).containsKey(fileMetadata.getId()))
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity())));
    }

    private void restoreDirectory(@NotNull final FileMetadata fileMetadata) {
        final var path = restoreTargets.mapToRestorePath(fileMetadata.getAbsolutePath());
        log.debug("Restoring directory: {}", path);
        try {
            if (Files.exists(path) && !Files.isDirectory(path)) {
                deleteIfExists(path);
            }
            createDirectory(path);
            log.debug("Restored directory: {}", path);
        } catch (final IOException e) {
            throw new ArchivalException("Failed to restore directory: " + path, e);
        }

    }

    private void restoreFileSequentially(
            @NotNull final InputStream inputStream,
            @NotNull final FileMetadata fileMetadata) {
        final var path = restoreTargets.mapToRestorePath(fileMetadata.getAbsolutePath());
        restoreFileContent(inputStream, path);
    }

    @Nullable
    private SecretKey getDecryptionKey(
            @NotNull final RestoreManifest relevantManifest,
            @NotNull final ArchiveEntryLocator entryName) {
        return Optional.ofNullable(kek)
                .map(k -> relevantManifest.dataDecryptionKey(k, entryName))
                .orElse(null);
    }

    @NotNull
    private BarjCargoArchiveFileInputStreamSource getStreamSource(
            @NotNull final RestoreManifest manifest,
            @NotNull final String fileNamePrefix) throws IOException {
        final var key = manifest.getVersions().first();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        streamLock.lock();
        try {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
            final var builder = BarjCargoInputStreamConfiguration.builder()
                    .folder(backupDirectory)
                    .prefix(fileNamePrefix)
                    .hashAlgorithm(manifest.getConfiguration().getHashAlgorithm().getAlgorithmName())
                    .compressionFunction(manifest.getConfiguration().getCompression()::decorateInputStream);
            Optional.ofNullable(kek)
                    .map(manifest::dataIndexDecryptionKey)
                    .ifPresent(builder::indexDecryptionKey);
            final var streamSource = new BarjCargoArchiveFileInputStreamSource(builder.build());
            manifest.getVersions().forEach(v -> cache.put(v, streamSource));
            return streamSource;
        } finally {
            streamLock.unlock();
        }
    }
}
