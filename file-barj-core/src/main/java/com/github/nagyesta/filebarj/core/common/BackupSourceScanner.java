package com.github.nagyesta.filebarj.core.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.persistence.FileSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FileSetId;
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

    private final FileSetRepository fileSetRepository;
    private final BackupSource backupSource;
    private final Path backupSourceOsPath;
    private final SortedSet<String> includePatterns;
    private final SortedSet<String> excludePatterns;
    private final boolean shouldUsePatterns;

    public BackupSourceScanner(
            final @NonNull FileSetRepository fileSetRepository,
            final @NonNull BackupSource backupSource) {
        this.fileSetRepository = fileSetRepository;
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
     * @param resultFileSetId the Id of the file se where we should collect the results
     */
    @JsonIgnore
    public void listMatchingFilePaths(final @NonNull FileSetId resultFileSetId) {
        try (var tempFileSetId = fileSetRepository.createFileSet()) {
            var current = Optional.of(backupSourceOsPath);
            var parentDirsFromBackupSource = List.<Path>of();
            Path lastParent = null;
            while (current.isPresent()) {
                final var currentPath = current.get();
                if (!currentPath.getParent().equals(lastParent)) {
                    lastParent = currentPath.getParent();
                    parentDirsFromBackupSource = findParentsFromBackupSource(currentPath);
                }
                listRemainingFiles(currentPath, parentDirsFromBackupSource, resultFileSetId, tempFileSetId);
                current = fileSetRepository.takeFirst(tempFileSetId);
            }
        }
    }

    private List<Path> findParentsFromBackupSource(final @NotNull Path path) {
        final var parents = new ArrayList<>(List.of(backupSourceOsPath));
        var current = path.getParent();
        while (current.startsWith(backupSourceOsPath)) {
            parents.add(current);
            current = current.getParent();
        }
        return parents;
    }

    private void listRemainingFiles(
            final @NotNull Path currentPath,
            final @NotNull List<Path> parentDirsFromBackupSource,
            final @NotNull FileSetId sourceFileSetIt,
            final @NotNull FileSetId tempFileSetId) {
        if (!currentPath.toFile().exists()) {
            return;
        }
        if (shouldIgnoreByExcludePattern(currentPath)) {
            return;
        }
        if (!Files.isDirectory(currentPath, LinkOption.NOFOLLOW_LINKS)) {
            if (shouldIgnoreFileByPatterns(currentPath)) {
                return;
            }
            //add all parents if they are not saved yet
            fileSetRepository.appendTo(sourceFileSetIt, parentDirsFromBackupSource);
            fileSetRepository.appendTo(sourceFileSetIt, currentPath);
        } else {
            if (isCurrentDirectoryInSourceSetByPatterns(currentPath)) {
                //always add directories if the include patterns match and the exclude patterns do not match
                fileSetRepository.appendTo(sourceFileSetIt, parentDirsFromBackupSource);
                fileSetRepository.appendTo(sourceFileSetIt, currentPath);
            }
            if (hasChildren(currentPath)) {
                Optional.ofNullable(currentPath.toFile().listFiles())
                        .stream()
                        .flatMap(Arrays::stream)
                        .map(File::toPath)
                        .filter(child -> !shouldIgnoreByExcludePattern(child))
                        .forEach((Path child) -> fileSetRepository.appendTo(tempFileSetId, child));
            }
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
