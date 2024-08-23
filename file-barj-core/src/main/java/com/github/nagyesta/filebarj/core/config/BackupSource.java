package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    @JsonProperty("path")
    private final @Valid
    @NonNull BackupPath path;
    /**
     * Optional include patterns for filtering the contents. Uses {@link java.nio.file.PathMatcher}
     * with "glob" syntax relative to the value of the path field.
     */
    @JsonProperty("include_patterns")
    private final Set<@NotNull @NotBlank String> includePatterns;
    /**
     * Optional exclude patterns for filtering the contents. Uses {@link java.nio.file.PathMatcher}
     * with "glob" syntax relative to the value of the path field.
     */
    @JsonProperty("exclude_patterns")
    private final Set<@NotNull @NotBlank String> excludePatterns;

    /**
     * Lists the matching {@link Path} entries.
     *
     * @return matching paths
     */
    @JsonIgnore
    public List<Path> listMatchingFilePaths() {
        return listFilesRecursive(path.toOsPath())
                .filter(this::includePatternsDoMatch)
                .flatMap(this::includeIntermediateDirectories)
                .filter(this::excludePatternsDoNotMatch)
                .distinct()
                .sorted(Comparator.comparing(Path::toAbsolutePath))
                .toList();
    }

    @Override
    public String toString() {
        final var includes = Optional.ofNullable(includePatterns)
                .filter(v -> !v.isEmpty())
                .orElse(Set.of(INCLUDE_ALL_FILES));
        final var excludes = Optional.ofNullable(excludePatterns)
                .filter(v -> !v.isEmpty())
                .orElse(Set.of());
        return "BackupSource{path=" + path + ", include=" + includes + ", exclude=" + excludes + "}";
    }

    private Stream<Path> includeIntermediateDirectories(final Path aPath) {
        final var pathAsStream = Stream.of(aPath);
        if (aPath.toAbsolutePath().equals(path.toOsPath())) {
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
        final var fileSystem = FileSystems.getDefault();
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
        final var fileSystem = FileSystems.getDefault();
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
        //must use OS path to avoid issues with Windows paths
        return ("glob:" + FilenameUtils.normalizeNoEndSeparator(path.toOsPath().toString()) + File.separator + pattern)
                //replace backslashes to let the regex conversion work later
                .replaceAll(Pattern.quote("\\"), "/");
    }

}
