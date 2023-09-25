package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ext.NioPathDeserializer;
import com.fasterxml.jackson.databind.ext.NioPathSerializer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents a backup source root. Can match a file or directory.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSource {
    /**
     * Universal pattern including all files.
     */
    private static final String INCLUDE_ALL_FILES = "**";
    /**
     * The path we want to back up. Can be file or directory.
     */
    @NonNull
    @JsonProperty("path")
    @JsonSerialize(using = NioPathSerializer.class)
    @JsonDeserialize(using = NioPathDeserializer.class)
    private final Path path;
    /**
     * Optional include patterns for filtering the contents.
     * Uses {@link java.nio.file.PathMatcher} with "glob" syntax
     * relative to the value of the path field.
     */
    @JsonProperty("includePatterns")
    private final Set<String> includePatterns;
    /**
     * Optional exclude patterns for filtering the contents.
     * Uses {@link java.nio.file.PathMatcher} with "glob" syntax
     * relative to the value of the path field.
     */
    @JsonProperty("excludePatterns")
    private final Set<String> excludePatterns;

    /**
     * Lists the matching {@link Path} entries.
     *
     * @return matching paths
     */
    @JsonIgnore
    public List<Path> listMatchingFilePaths() {
        return listFilesRecursive(path.toAbsolutePath())
                .filter(this::includePatternsDoMatch)
                .flatMap(this::includeIntermediateDirectories)
                .filter(this::excludePatternsDoNotMatch)
                .distinct()
                .sorted(Comparator.comparing(Path::toAbsolutePath))
                .toList();
    }

    private Stream<Path> includeIntermediateDirectories(final Path aPath) {
        final Stream<Path> pathAsStream = Stream.of(aPath);
        if (aPath.toAbsolutePath().equals(path)) {
            return pathAsStream;
        } else {
            return Stream.of(pathAsStream, includeIntermediateDirectories(aPath.getParent()))
                    .flatMap(Function.identity());
        }
    }

    private Stream<Path> listFilesRecursive(final Path fromRoot) {
        if (!fromRoot.toFile().exists()) {
            return Stream.empty();
        } else if (!Files.isDirectory(fromRoot) || hasNoChildren(fromRoot)) {
            return Stream.of(fromRoot);
        } else {
            return Optional.ofNullable(fromRoot.toFile().listFiles())
                    .stream()
                    .flatMap(Arrays::stream)
                    .map(File::toPath)
                    .flatMap(this::listFilesRecursive);
        }
    }

    private boolean hasNoChildren(final Path dirPath) {
        return Optional.ofNullable(dirPath.toFile().list())
                .map(List::of)
                .orElse(Collections.emptyList())
                .isEmpty();
    }

    private boolean includePatternsDoMatch(final Path toFilter) {
        if (!path.toFile().isDirectory()) {
            assertHasNoPatterns(includePatterns, "Include");
            return true;
        }
        final FileSystem fileSystem = FileSystems.getDefault();
        return Optional.ofNullable(includePatterns)
                .filter(v -> !v.isEmpty())
                .orElse(Set.of(INCLUDE_ALL_FILES))
                .stream()
                .map(this::translatePattern)
                .map(fileSystem::getPathMatcher)
                .anyMatch(matcher -> matcher.matches(toFilter.toAbsolutePath()));
    }

    private boolean excludePatternsDoNotMatch(final Path toFilter) {
        if (!path.toFile().isDirectory()) {
            assertHasNoPatterns(excludePatterns, "Exclude");
            return true;
        }
        final FileSystem fileSystem = FileSystems.getDefault();
        return Optional.ofNullable(excludePatterns)
                .orElse(Set.of())
                .stream()
                .map(this::translatePattern)
                .map(fileSystem::getPathMatcher)
                .noneMatch(matcher -> matcher.matches(toFilter.toAbsolutePath()));
    }

    private void assertHasNoPatterns(final Set<String> patterns, final String prefix) {
        if (!Optional.ofNullable(patterns).orElse(Collections.emptySet()).isEmpty()) {
            throw new IllegalArgumentException(
                    prefix + " patterns cannot be defined when the backup source is not a directory: " + path);
        }

    }

    private String translatePattern(final String pattern) {
        return "glob:" + path.toAbsolutePath() + File.separator + pattern;
    }
}
