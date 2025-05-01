package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a backup source root. Can match a file or directory.
 */
@JsonDeserialize(builder = BackupSource.BackupSourceBuilder.class)
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSource {
    /**
     * Universal pattern including all files.
     */
    private static final Set<String> INCLUDE_ALL_FILES = Set.of("**");
    /**
     * Universal pattern not excluding any files.
     */
    private static final Set<String> EXCLUDE_NO_FILES = Set.of();
    /**
     * The path we want to back up. Can be file or directory.
     */
    @Getter
    @JsonProperty("path")
    private final @Valid
    @NonNull BackupPath path;
    /**
     * Optional include patterns for filtering the contents. Uses {@link java.nio.file.PathMatcher}
     * with "glob" syntax relative to the value of the path field.
     */
    @Getter
    @JsonProperty("include_patterns")
    private final Set<@NotNull @NotBlank String> includePatterns;
    /**
     * Optional exclude patterns for filtering the contents. Uses {@link java.nio.file.PathMatcher}
     * with "glob" syntax relative to the value of the path field.
     */
    @Getter
    @JsonProperty("exclude_patterns")
    private final Set<@NotNull @NotBlank String> excludePatterns;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private final boolean shouldIgnorePatterns;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private final Set<String> preprocessedIncludePatterns;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private final Set<String> preprocessedExcludePatterns;

    BackupSource(
            final @Valid @NonNull BackupPath path,
            final Set<@NotNull @NotBlank String> includePatterns,
            final Set<@NotNull @NotBlank String> excludePatterns) {
        this.path = path;
        this.includePatterns = Optional.ofNullable(includePatterns).map(Set::copyOf).orElse(Collections.emptySet());
        this.shouldIgnorePatterns = shouldIgnorePatternsDueToPathType(path, includePatterns, excludePatterns);
        this.preprocessedIncludePatterns = preprocessPatterns(coalescePatterns(includePatterns, INCLUDE_ALL_FILES));
        this.excludePatterns = Optional.ofNullable(excludePatterns).map(Set::copyOf).orElse(Collections.emptySet());
        this.preprocessedExcludePatterns = preprocessPatterns(coalescePatterns(excludePatterns, EXCLUDE_NO_FILES));
    }

    public static BackupSourceBuilder builder() {
        return new BackupSourceBuilder();
    }

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
        final var includes = coalescePatterns(includePatterns, INCLUDE_ALL_FILES);
        final var excludes = coalescePatterns(excludePatterns, EXCLUDE_NO_FILES);
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
        } else if (!Files.isDirectory(fromRoot, LinkOption.NOFOLLOW_LINKS) || hasNoChildren(fromRoot)) {
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
        if (shouldIgnorePatterns) {
            return true;
        }
        final var fileSystem = FileSystems.getDefault();
        return preprocessedIncludePatterns.stream()
                .map(fileSystem::getPathMatcher)
                .anyMatch(matcher -> matcher.matches(toFilter.toAbsolutePath()));
    }

    private boolean excludePatternsDoNotMatch(final Path toFilter) {
        if (shouldIgnorePatterns) {
            return true;
        }
        final var fileSystem = FileSystems.getDefault();
        return preprocessedExcludePatterns.stream()
                .map(fileSystem::getPathMatcher)
                .noneMatch(matcher -> matcher.matches(toFilter.toAbsolutePath()));
    }

    private boolean shouldIgnorePatternsDueToPathType(
            final BackupPath rootPath,
            final Set<String> includePatterns,
            final Set<String> excludePatterns) {
        //if the file does not exist, do not care about the type, it does not matter
        if (Files.exists(rootPath.toOsPath()) && !Files.isDirectory(rootPath.toOsPath(), LinkOption.NOFOLLOW_LINKS)) {
            assertHasNoPatterns(includePatterns, "Include");
            assertHasNoPatterns(excludePatterns, "Exclude");
            return true;
        }
        return false;
    }

    private void assertHasNoPatterns(
            final Set<String> patterns,
            final String prefix) {
        if (!Optional.ofNullable(patterns).orElse(Collections.emptySet()).isEmpty()) {
            throw new IllegalArgumentException(
                    prefix + " patterns cannot be defined when the backup source is not a directory: " + path);
        }

    }

    private Set<String> coalescePatterns(
            final Set<String> patterns,
            final Set<String> defaultValue) {
        return Optional.ofNullable(patterns)
                .filter(set -> !set.isEmpty())
                .orElse(defaultValue);
    }

    private SortedSet<String> preprocessPatterns(final Set<String> patterns) {
        return Collections.unmodifiableSortedSet(patterns.stream()
                .map(this::translatePattern)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    private String translatePattern(final String pattern) {
        //must use OS path to avoid issues with Windows paths
        return ("glob:" + FilenameUtils.normalizeNoEndSeparator(path.toOsPath().toString()) + File.separator + pattern)
                //replace backslashes to let the regex conversion work later
                .replaceAll(Pattern.quote("\\"), "/");
    }

    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PACKAGE)
    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "", buildMethodName = "build")
    public static class BackupSourceBuilder {
        private BackupPath path;
        private Set<String> includePatterns;
        private Set<String> excludePatterns;

        @JsonProperty("path")
        public BackupSourceBuilder path(final @Valid @NonNull BackupPath path) {
            this.path = path;
            return this;
        }

        @JsonProperty("include_patterns")
        public BackupSourceBuilder includePatterns(final Set<@NotNull @NotBlank String> includePatterns) {
            this.includePatterns = includePatterns;
            return this;
        }

        @JsonProperty("exclude_patterns")
        public BackupSourceBuilder excludePatterns(final Set<@NotNull @NotBlank String> excludePatterns) {
            this.excludePatterns = excludePatterns;
            return this;
        }

        public BackupSource build() {
            return new BackupSource(this.path, this.includePatterns, this.excludePatterns);
        }
    }
}
