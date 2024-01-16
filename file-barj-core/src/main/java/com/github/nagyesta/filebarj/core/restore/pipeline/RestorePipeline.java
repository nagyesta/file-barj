package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.common.FileMetadataChangeDetector;
import com.github.nagyesta.filebarj.core.common.FileMetadataChangeDetectorFactory;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.restore.worker.FileMetadataSetter;
import com.github.nagyesta.filebarj.core.restore.worker.FileMetadataSetterFactory;
import com.github.nagyesta.filebarj.core.util.StatLogUtil;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream.MEBIBYTE;

/**
 * Performs the actual restore process as instructed by the controller.
 */
@Slf4j
public class RestorePipeline {

    private final Map<String, BarjCargoArchiveFileInputStreamSource> cache = new ConcurrentHashMap<>();
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
        this.fileMetadataSetter = FileMetadataSetterFactory.newInstance(restoreTargets);
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
     * @param contentSources the files with content to restore
     * @param threadPool     the thread pool we can use for parallel processing
     */
    @SuppressWarnings("checkstyle:TodoComment")
    public void restoreFiles(
            @NonNull final Collection<FileMetadata> contentSources,
            @NonNull final ForkJoinPool threadPool) {
        log.info("Restoring {} items", contentSources.size());
        //TODO: Deletions should not be ignored. We need to perform them in a separate step
        final var changeStatus = detectChanges(contentSources, threadPool, false);
        final var pathsToRestore = contentSources.stream()
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toSet());
        final var files = manifest.getFilesOfLastManifest();
        files.values().stream()
                .filter(fileMetadata -> fileMetadata.getError() != null)
                .forEach(fileMetadata -> log.warn("File {} might be corrupted. The following error was saved during backup:\n  {}",
                        fileMetadata.getAbsolutePath(), fileMetadata.getError()));
        final var entries = manifest.getArchivedEntriesOfLastManifest();
        final var restoreScope = new RestoreScope(files, entries, changeStatus, pathsToRestore);
        final var filesWithContentChanges = restoreScope.getChangedContentSourcesByPath();
        final var itemsCount = filesWithContentChanges.size();
        final var contentSize = filesWithContentChanges.values().stream()
                .mapToLong(FileMetadata::getOriginalSizeBytes)
                .sum();
        log.info("Unpacking {} items with content changes ({} MiB)", itemsCount, contentSize / MEBIBYTE);
        final var startTimeMillis = System.currentTimeMillis();
        restoreContent(threadPool, restoreScope, contentSources.size());
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Content is unpacked. Completed under: {}", toProcessSummary(durationMillis, contentSize));
    }

    /**
     * Finalizes the permissions of the restored files.
     *
     * @param files      The files in scope
     * @param threadPool The thread pool we can use for parallel processing
     */
    public void finalizePermissions(
            @NonNull final List<FileMetadata> files,
            @NonNull final ForkJoinPool threadPool) {
        log.info("Finalizing metadata for {} files", files.size());
        final var changeStatus = detectChanges(files, threadPool, false);
        final var filesWithMetadataChanges = files.stream()
                .filter(entry -> changeStatus.get(entry.getAbsolutePath()).isRestoreMetadata())
                .toList();
        final var doneCount = new AtomicInteger();
        filesWithMetadataChanges.stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath).reversed())
                .forEachOrdered(fileMetadata -> {
                    setFileProperties(fileMetadata);
                    StatLogUtil.logIfThresholdReached(doneCount.incrementAndGet(), filesWithMetadataChanges.size(),
                            (done, total) -> log.info("Finalized metadata for {} of {} paths.", done, total));
                });
        final var totalCount = StatLogUtil.countsByType(files);
        final var changedCount = StatLogUtil.countsByType(filesWithMetadataChanges);
        changedCount.keySet().forEach(type -> log.info("Finalized metadata for {} of {} {} entries.",
                changedCount.get(type), totalCount.get(type), type));
    }

    /**
     * Checks the file statuses after the restore is completed and reports any files that could not
     * be fully restored (either having the wrong content or the wrong metadata).
     *
     * @param files      The files to check
     * @param threadPool The thread pool
     */
    public void evaluateRestoreSuccess(
            @NonNull final List<FileMetadata> files,
            @NonNull final ForkJoinPool threadPool) {
        final var checkOutcome = detectChanges(files, threadPool, true);
        files.stream()
                //cannot verify symbolic links because they can be referencing files from the
                //restore folder which is not necessarily the same as the original backup folder
                .filter(entry -> entry.getFileType() != FileType.SYMBOLIC_LINK)
                .filter(entry -> checkOutcome.get(entry.getAbsolutePath()).isRestoreMetadata())
                .forEach(file -> {
                    final var change = Optional.ofNullable(checkOutcome.get(file.getAbsolutePath()))
                            .map(Change::getRestoreStatusMessage)
                            .orElse("Unknown.");
                    final var restorePath = restoreTargets.mapToRestorePath(file.getAbsolutePath());
                    log.warn("Could not fully restore item.\n  Path: {} \n  Status: {}", restorePath, change);
                });
    }

    /**
     * Sets the file properties for the specified file based on the file metadata saved during backup.
     *
     * @param fileMetadata the file metadata
     */
    protected void setFileProperties(@NotNull final FileMetadata fileMetadata) {
        final var restorePath = restoreTargets.mapToRestorePath(fileMetadata.getAbsolutePath());
        if (!Files.exists(restorePath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
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
     * Removes the existing file and creates a symbolic link to point ot the desired target.
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
                deleteIfExists(copy);
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
        createParentDirectoryAsFallbackIfMissing(target);
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
        if (!Files.exists(currentFile, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(currentFile, LinkOption.NOFOLLOW_LINKS)) {
            FileUtils.deleteDirectory(currentFile.toFile());
        } else {
            Files.delete(currentFile);
        }
    }

    private void createParentDirectoryAsFallbackIfMissing(@NotNull final Path target) {
        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                log.warn("Creating missing parent directory: {}", target.getParent());
                Files.createDirectories(target.getParent());
            }
        } catch (final IOException e) {
            throw new ArchivalException("Failed to restore content: " + target, e);
        }
    }

    private void restoreContent(
            final ForkJoinPool threadPool,
            @NotNull final RestoreScope restoreScope,
            final int totalFiles) {
        final var changedContentSourcesByPath = restoreScope.getChangedContentSourcesByPath();
        final var size = changedContentSourcesByPath.size();
        final var restoreSize = changedContentSourcesByPath.values().stream()
                .map(FileMetadata::getOriginalSizeBytes)
                .mapToLong(Long::longValue)
                .sum() / MEBIBYTE;
        log.info("Restoring {} entries with content changes ({} MiB).", size, restoreSize);
        final var linkPaths = new ConcurrentHashMap<FileMetadata, Path>();
        final var contentSourcesInScopeByLocator = restoreScope.getContentSourcesInScopeByLocator();
        final var scopePerManifest = manifest.getFileNamePrefixes().keySet().stream()
                .collect(Collectors.toMap(Function.identity(),
                        prefix -> new ConcurrentHashMap<ArchiveEntryLocator, SortedSet<FileMetadata>>()));
        threadPool.submit(() -> manifest.getFileNamePrefixes()
                .entrySet()
                .parallelStream()
                .forEach(prefixEntry -> {
                    final var prefix = prefixEntry.getKey();
                    final var versions = prefixEntry.getValue();
                    contentSourcesInScopeByLocator.entrySet().stream()
                            //keep only those entries that have the same version as the archive
                            .filter(entry -> versions.contains(entry.getKey().getBackupIncrement()))
                            //assign the files from the scope to the archive prefix
                            .forEach(entry -> scopePerManifest.get(prefix).put(entry.getKey(), entry.getValue()));
                })).join();
        log.info("Found {} manifests.", scopePerManifest.size());
        scopePerManifest.forEach((key, value) -> {
            final var entryCount = value.size();
            final var fileCount = value.values().stream().mapToLong(Collection::size).sum();
            log.info("Manifest {} has {} archive entries ({} files) in scope.", key, entryCount, fileCount);
        });
        final var partitions = new ArrayList<Map.Entry<String, Map<ArchiveEntryLocator, SortedSet<FileMetadata>>>>();
        scopePerManifest.forEach((prefix, scope) -> {
            final var archiveEntryPathsInScope = threadPool.submit(() -> scope.keySet().parallelStream()
                    .map(ArchiveEntryLocator::asEntryPath)
                    .collect(Collectors.toSet())).join();
            log.info("Found {} archive entries in scope for prefix: {}.", archiveEntryPathsInScope.size(), prefix);
            if (archiveEntryPathsInScope.isEmpty()) {
                return;
            }
            try {
                final var matchingEntriesInOrderOfOccurrence = getStreamSource(manifest, prefix)
                        .getMatchingEntriesInOrderOfOccurrence(archiveEntryPathsInScope);
                log.info("Found {} entries in archive with prefix: {}.", matchingEntriesInOrderOfOccurrence.size(), prefix);
                partition(matchingEntriesInOrderOfOccurrence, scope, threadPool.getParallelism()).stream()
                        .filter(chunk -> !chunk.isEmpty())
                        .forEach(chunk -> {
                            log.info("Adding a partition with {} entries for prefix: {}", chunk.size(), prefix);
                            partitions.add(new AbstractMap.SimpleEntry<>(prefix, chunk));
                        });
            } catch (final IOException e) {
                throw new ArchivalException("Failed to filter archive entries.", e);
            }
        });
        log.info("Formed {} partitions", partitions.size());
        threadPool.submit(() -> partitions.parallelStream().forEach(entry -> {
            final var prefix = entry.getKey();
            final var scope = entry.getValue();
            final var resolvedLinks = restoreMatchingEntriesOfManifest(manifest, restoreScope, prefix, scope);
            linkPaths.putAll(resolvedLinks);
        })).join();
        createSymbolicLinks(linkPaths, threadPool);
        log.info("Restored {} changed entries of {} files", size, totalFiles);
    }

    @NotNull
    private List<Map<ArchiveEntryLocator, SortedSet<FileMetadata>>> partition(
            @NotNull final List<BarjCargoEntityIndex> input,
            @NotNull final Map<ArchiveEntryLocator, SortedSet<FileMetadata>> contentSourcesInScopeByLocator,
            final int partitions) {
        final var chunks = new ArrayList<Map<ArchiveEntryLocator, SortedSet<FileMetadata>>>();
        final var threshold = (input.size() / partitions) + 1;
        var collector = new HashMap<ArchiveEntryLocator, SortedSet<FileMetadata>>(threshold);
        for (final var file : input) {
            if (collector.size() >= threshold) {
                chunks.add(collector);
                collector = new HashMap<>(threshold);
            }
            final var currentLocator = ArchiveEntryLocator.fromEntryPath(file.getPath());
            if (currentLocator == null) {
                throw new ArchivalException("Failed to extract locator from entry path: " + file.getPath());
            }
            final var fileMetadataList = contentSourcesInScopeByLocator.get(currentLocator);
            collector.put(currentLocator, fileMetadataList);
        }
        chunks.add(collector);
        return chunks;
    }

    private Map<FileMetadata, Path> restoreMatchingEntriesOfManifest(
            @NotNull final RestoreManifest manifest,
            @NotNull final RestoreScope restoreScope,
            @NotNull final String prefix,
            @NotNull final Map<ArchiveEntryLocator, SortedSet<FileMetadata>> contentSourcesInScopeByLocator) {
        final var remaining = contentSourcesInScopeByLocator
                .keySet().stream()
                .filter(locator -> manifest.getFileNamePrefixes().get(prefix).contains(locator.getBackupIncrement()))
                .collect(Collectors.toSet());
        log.info("Found {} entries in manifest with prefix: {}", remaining.size(), prefix);
        final var linkPaths = new HashMap<FileMetadata, Path>();
        try {
            final var streamSource = getStreamSource(manifest, prefix);
            final var it = streamSource.getIteratorForScope(remaining.stream()
                    .map(ArchiveEntryLocator::asEntryPath)
                    .collect(Collectors.toSet()));
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
                if (skipIfNotInScope(contentSourcesInScopeByLocator.keySet(), archiveEntry)) {
                    continue;
                }
                final var entries = contentSourcesInScopeByLocator.get(locator);
                final var type = getSingleEntryType(entries, locator.getEntryName());
                if (Objects.requireNonNull(type) == FileType.REGULAR_FILE) {
                    restoreFileContent(archiveEntry, entries.first(), key);
                    copyRestoredFileToRemainingLocations(entries.first(), entries.stream().skip(1).toList());
                    remaining.remove(locator);
                    skipMetadata(archiveEntry);
                } else if (type == FileType.SYMBOLIC_LINK) {
                    final var targetPath = resolveLinkTarget(archiveEntry, entries.first(), key, restoreScope);
                    entries.forEach(metadata -> {
                        linkPaths.put(metadata, targetPath);
                        remaining.remove(locator);
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
    private Map<Path, Change> detectChanges(
            @NotNull final Collection<FileMetadata> files,
            @NotNull final ForkJoinPool threadPool,
            final boolean ignoreLinks) {
        final var parser = FileMetadataParserFactory.newInstance();
        final Map<Path, Change> changeStatuses = new ConcurrentHashMap<>();
        final var doneCount = new AtomicInteger();
        threadPool.submit(() -> files.parallelStream()
                .filter(fileMetadata -> !ignoreLinks || fileMetadata.getFileType() != FileType.SYMBOLIC_LINK)
                .forEach(file -> {
                    final var previous = changeDetector.findPreviousVersionByAbsolutePath(file.getAbsolutePath());
                    if (previous == null) {
                        throw new IllegalStateException("Previous version not found for " + file.getAbsolutePath());
                    }
                    final var restorePath = restoreTargets.mapToRestorePath(file.getAbsolutePath());
                    final var current = parser.parse(restorePath.toFile(), manifest.getConfiguration());
                    final var change = changeDetector.classifyChange(previous, current);
                    StatLogUtil.logIfThresholdReached(doneCount.incrementAndGet(), files.size(),
                            (done, total) -> log.info("Parsed {} of {} unique paths.", done, total));
                    changeStatuses.put(file.getAbsolutePath(), change);
                })).join();
        final var stats = new TreeMap<>(changeStatuses.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
        log.info("Detected changes: {}", stats);
        return changeStatuses;
    }

    private void createSymbolicLinks(@NotNull final ConcurrentHashMap<FileMetadata, Path> linkPaths, final ForkJoinPool threadPool) {
        threadPool.submit(() -> linkPaths.entrySet().parallelStream().forEach(entry -> {
            final var metadata = entry.getKey();
            final var linkTarget = entry.getValue();
            final var symbolicLink = restoreTargets.mapToRestorePath(metadata.getAbsolutePath());
            try {
                if (shouldCreateNewLink(linkTarget, symbolicLink)) {
                    deleteIfExists(symbolicLink);
                    createSymbolicLink(linkTarget, symbolicLink);
                }
            } catch (final IOException e) {
                throw new ArchivalException("Failed to create symbolic link: " + symbolicLink + " pointing to: " + linkTarget, e);
            }
        })).join();
    }

    private boolean shouldCreateNewLink(final Path linkTarget, final Path to) throws IOException {
        var linkNeeded = true;
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(to)) {
            final var currentTarget = IOUtils.toString(FileType.SYMBOLIC_LINK.streamContent(to), StandardCharsets.UTF_8);
            if (currentTarget.equals(linkTarget.toString())) {
                log.debug("Found existing link: {} correctly pointing to: {}", to, currentTarget);
                linkNeeded = false;
            } else {
                log.debug("Found existing link: {} pointing to: {} instead of: {}", to, currentTarget, linkTarget);
            }
        } else if (!Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            log.debug("Link does not exist: {}", to);
        } else if (!Files.isSymbolicLink(to)) {
            log.debug("File exist, but is not symbolic link: {}", to);
        }
        return linkNeeded;
    }

    private boolean skipIfNotInScope(
            @NotNull final Set<ArchiveEntryLocator> archiveEntryPathsInScope,
            @NotNull final SequentialBarjCargoArchiveEntry archiveEntry) {
        final var locator = ArchiveEntryLocator.fromEntryPath(archiveEntry.getPath());
        if (locator == null) {
            return true;
        } else if (archiveEntryPathsInScope.contains(locator)) {
            return false;
        }
        try {
            archiveEntry.skipContent();
            archiveEntry.skipMetadata();
            return true;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to skip content and metadata.", e);
        }
    }

    private FileType getSingleEntryType(
            @NotNull final Collection<FileMetadata> files,
            @NotNull final UUID archiveEntryId) {
        final var types = files.stream()
                .map(FileMetadata::getFileType)
                .collect(Collectors.toCollection(TreeSet::new));
        if (types.size() > 1) {
            throw new ArchiveIntegrityException("Multiple types found for entry: " + archiveEntryId);
        }
        return types.first();
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
            @NotNull final RestoreScope restoreScope) {
        final var to = restoreTargets.mapToRestorePath(fileMetadata.getAbsolutePath());
        try {
            final var target = archiveEntry.getLinkTarget(key);
            final var pointsToAFileCoveredByTheBackup = restoreScope.getAllKnownPathsInBackup().stream()
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
            throw new ArchivalException("Failed to skip metadata.", e);
        }
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
        if (cache.containsKey(fileNamePrefix)) {
            return cache.get(fileNamePrefix);
        }
        streamLock.lock();
        try {
            if (cache.containsKey(fileNamePrefix)) {
                return cache.get(fileNamePrefix);
            }
            final var builder = BarjCargoInputStreamConfiguration.builder()
                    .folder(backupDirectory)
                    .prefix(fileNamePrefix)
                    .hashAlgorithm(manifest.getConfiguration().getHashAlgorithm().getAlgorithmName())
                    .compressionFunction(manifest.getConfiguration().getCompression()::decorateInputStream);
            Optional.ofNullable(kek)
                    .map(key -> manifest.dataIndexDecryptionKey(fileNamePrefix, key))
                    .ifPresent(builder::indexDecryptionKey);
            final var streamSource = new BarjCargoArchiveFileInputStreamSource(builder.build());
            cache.put(fileNamePrefix, streamSource);
            return streamSource;
        } finally {
            streamLock.unlock();
        }
    }
}
