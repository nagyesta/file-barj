package com.github.nagyesta.filebarj.core.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.persistence.FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
import lombok.NonNull;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.persistence.DataStore.BATCH_CHUNK_SIZE;

/**
 * Scans the files present in a backup source root.
 */
public class BackupSourceScanner {
    /**
     * Universal pattern including all files.
     */
    private static final Set<String> INCLUDE_ALL_FILES = Set.of("**");
    /**
     * Universal pattern not excluding any files.
     */
    private static final Set<String> EXCLUDE_NO_FILES = Set.of();

    private final FilePathSetRepository filePathSetRepository;
    private final BackupSource backupSource;
    private final Path backupSourceOsPath;
    private final SortedSet<String> includePatterns;
    private final SortedSet<String> excludePatterns;
    private final boolean shouldUsePatterns;

    public BackupSourceScanner(
            final @NonNull FilePathSetRepository filePathSetRepository,
            final @NonNull BackupSource backupSource) {
        this.filePathSetRepository = filePathSetRepository;
        this.backupSource = backupSource;
        this.backupSourceOsPath = backupSource.getPath().toOsPath();
        final var originalIncludes = backupSource.getIncludePatterns();
        final var originalExcludes = backupSource.getExcludePatterns();
        this.shouldUsePatterns = pathTypeCanUsePatterns(backupSource.getPath(), originalIncludes, originalExcludes);
        this.includePatterns = preProcessPatterns(coalescePatterns(originalIncludes, INCLUDE_ALL_FILES));
        this.excludePatterns = preProcessPatterns(coalescePatterns(originalExcludes, EXCLUDE_NO_FILES));
    }

    /**
     * Lists the matching {@link Path} entries.
     *
     * @param resultFilePathSetId the Id of the file se where we should collect the results
     */
    @JsonIgnore
    public void listMatchingFilePaths(final @NonNull FilePathSetId resultFilePathSetId) {
            var current = Optional.of(backupSourceOsPath);
            final var toBeProcessed = new ArrayDeque<Path>(BATCH_CHUNK_SIZE);
            var parentDirsFromBackupSource = Set.<Path>of();
            Path lastParent = null;
            final var buffer = new HashSet<Path>(BATCH_CHUNK_SIZE);
            while (current.isPresent()) {
                final var currentPath = current.get();
                if (!currentPath.getParent().equals(lastParent)) {
                    lastParent = currentPath.getParent();
                    parentDirsFromBackupSource = findParentsFromBackupSource(currentPath);
                }
                final var newPaths = listRemainingFiles(currentPath, parentDirsFromBackupSource, toBeProcessed);
                if (newPaths.size() + buffer.size() > BATCH_CHUNK_SIZE) {
                    filePathSetRepository.appendTo(resultFilePathSetId, buffer);
                    buffer.clear();
                }
                buffer.addAll(newPaths);
                if (toBeProcessed.isEmpty()) {
                    current = Optional.empty();
                } else {
                    current = Optional.of(toBeProcessed.pop());
                }
            }
            filePathSetRepository.appendTo(resultFilePathSetId, buffer);
    }

    private Set<Path> findParentsFromBackupSource(final @NotNull Path path) {
        final var parents = new LinkedHashSet<>(Set.of(backupSourceOsPath));
        var current = path.getParent();
        while (current.startsWith(backupSourceOsPath)) {
            parents.add(current);
            current = current.getParent();
        }
        return parents;
    }

    private Set<Path> listRemainingFiles(
            final @NotNull Path currentPath,
            final @NotNull Set<Path> parentDirsFromBackupSource,
            final @NotNull Deque<Path> toBeProcessedLater) {
        if (!currentPath.toFile().exists()) {
            return Collections.emptySet();
        }
        if (shouldIgnoreByExcludePattern(currentPath)) {
            return Collections.emptySet();
        }
        if (!Files.isDirectory(currentPath, LinkOption.NOFOLLOW_LINKS)) {
            if (shouldIgnoreFileByPatterns(currentPath)) {
                return Collections.emptySet();
            }
            //add all parents if they are not saved yet
            final var result = new LinkedHashSet<>(parentDirsFromBackupSource);
            result.add(currentPath);
            return result;
        } else {
            final var result = new LinkedHashSet<Path>();
            if (isCurrentDirectoryInSourceSetByPatterns(currentPath)) {
                //always add directories if the include patterns match and the exclude patterns do not match
                result.addAll(parentDirsFromBackupSource);
                result.add(currentPath);

            }
            if (hasChildren(currentPath)) {
                Optional.ofNullable(currentPath.toFile().listFiles())
                        .stream()
                        .flatMap(Arrays::stream)
                        .map(File::toPath)
                        .filter(child -> !shouldIgnoreByExcludePattern(child))
                        .forEach(toBeProcessedLater::add);
            }
            return result;
        }
    }

    private boolean isCurrentDirectoryInSourceSetByPatterns(final @NotNull Path currentPath) {
        return shouldUsePatterns && includePatternsMatch(currentPath) && !excludePatternsMatch(currentPath);
    }

    private boolean shouldIgnoreByExcludePattern(final @NotNull Path currentPath) {
        return shouldUsePatterns && excludePatternsMatch(currentPath);
    }

    private boolean shouldIgnoreFileByPatterns(final @NotNull Path currentPath) {
        return shouldUsePatterns && (!includePatternsMatch(currentPath) || excludePatternsMatch(currentPath));
    }

    private boolean hasChildren(final @NotNull Path dirPath) {
        return Optional.ofNullable(dirPath.toFile().list())
                .filter(array -> array.length > 0)
                .isPresent();
    }

    private boolean includePatternsMatch(final @NotNull Path toFilter) {
        return anyPatternsMatch(includePatterns, toFilter);
    }

    private boolean excludePatternsMatch(final @NotNull Path toFilter) {
        return anyPatternsMatch(excludePatterns, toFilter);
    }

    private boolean anyPatternsMatch(
            final @NotNull SortedSet<String> patterns,
            final @NotNull Path toFilter) {
        final var fileSystem = FileSystems.getDefault();
        return patterns.stream()
                .map(fileSystem::getPathMatcher)
                .anyMatch(matcher -> matcher.matches(toFilter.toAbsolutePath()));
    }

    private boolean pathTypeCanUsePatterns(
            final @NotNull BackupPath rootPath,
            final @NotNull Set<String> includePatterns,
            final @NotNull Set<String> excludePatterns) {
        //if the file does not exist, do not care about the type, it does not matter
        if (Files.exists(rootPath.toOsPath()) && !Files.isDirectory(rootPath.toOsPath(), LinkOption.NOFOLLOW_LINKS)) {
            assertHasNoPatterns(includePatterns, "Include");
            assertHasNoPatterns(excludePatterns, "Exclude");
            return false;
        }
        return true;
    }

    private void assertHasNoPatterns(
            final @Nullable Set<String> patterns,
            final @NotNull String prefix) {
        if (!Optional.ofNullable(patterns).orElse(Collections.emptySet()).isEmpty()) {
            throw new IllegalArgumentException(
                    prefix + " patterns cannot be defined when the backup source is not a directory: " + backupSource.getPath());
        }
    }

    private @NotNull Set<String> coalescePatterns(
            final @Nullable Set<String> patterns,
            final @NotNull Set<String> defaultValue) {
        return Optional.ofNullable(patterns)
                .filter(set -> !set.isEmpty())
                .orElse(defaultValue);
    }

    private @NotNull SortedSet<String> preProcessPatterns(final @NotNull Set<String> patterns) {
        return Collections.unmodifiableSortedSet(patterns.stream()
                .map(this::translatePattern)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    private @NotNull String translatePattern(final @NotNull String pattern) {
        //must use OS path to avoid issues with Windows paths
        return ("glob:" + FilenameUtils.normalizeNoEndSeparator(backupSource.getPath().toOsPath().toString()) + File.separator + pattern)
                //replace backslashes to let the regex conversion work later
                .replaceAll(Pattern.quote("\\"), "/");
    }
}
