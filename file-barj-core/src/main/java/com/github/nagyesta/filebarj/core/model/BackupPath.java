package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a path in the backup.
 */
@EqualsAndHashCode(of = "path")
public final class BackupPath implements Comparable<BackupPath> {

    private static final Comparator<BackupPath> PATH_COMPARATOR = Comparator.comparing(BackupPath::toOsPath);
    private static final String UNIX_SEPARATOR = "/";
    private static final String FILE_SCHEME_SINGLE_SLASH = "file:/";
    private static final String FILE_SCHEME_DOUBLE_SLASH = "file://";
    private static final String FILE_SCHEME_TRIPLE_SLASH = "file:///";
    private static final String PATH_GROUP = "path";
    private static final Pattern WINDOWS_FILE_SCHEME = Pattern
            .compile("^(" + FILE_SCHEME_SINGLE_SLASH + "|" + FILE_SCHEME_TRIPLE_SLASH + ")(?<" + PATH_GROUP + ">[A-Z]:/.*)$");
    private static final Pattern UNIX_FILE_SCHEME = Pattern
            .compile("^" + FILE_SCHEME_DOUBLE_SLASH + "(?<" + PATH_GROUP + ">/[^:]*)$");
    private static final Set<Pattern> PATTERNS = Set.of(WINDOWS_FILE_SCHEME, UNIX_FILE_SCHEME);
    @NotBlank
    private final String path;

    /**
     * Creates a new instance and sets the path.
     *
     * @param uri the file:// URI of the path
     * @return the new instance
     * @throws IllegalArgumentException if the uri does not start with file://
     */
    @JsonCreator
    public static BackupPath fromUri(@NonNull final String uri) {
        return PATTERNS.stream()
                .map(p -> {
                    final var matcher = p.matcher(uri);
                    if (matcher.matches()) {
                        return matcher.group(PATH_GROUP);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .map(BackupPath::new)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid uri: " + uri));
    }

    /**
     * Creates a new instance and sets the path.
     *
     * @param path the absolute OS path
     * @return the new instance
     */
    public static BackupPath ofPathAsIs(@NonNull final String path) {
        return new BackupPath(normalizePath(path));
    }

    /**
     * Creates a new instance and sets the path.
     *
     * @param prefix the path prefix
     * @param suffix the suffix of the path
     * @return the new instance
     */
    public static BackupPath of(@NonNull final Path prefix, @NonNull final String suffix) {
        return BackupPath.ofPathAsIs(prefix.toAbsolutePath() + UNIX_SEPARATOR + suffix);
    }

    /**
     * Creates a new instance and sets the path.
     *
     * @param prefix the path prefix
     * @param middle the middle part of the path
     * @param suffix the suffix of the path
     * @return the new instance
     */
    public static BackupPath of(@NonNull final Path prefix, @NonNull final String middle, @NonNull final String suffix) {
        return BackupPath.of(prefix, middle + UNIX_SEPARATOR + suffix);
    }

    /**
     * Creates a new instance and sets the path.
     *
     * @param path the OS path
     * @return the new instance
     */
    public static BackupPath of(@NonNull final Path path) {
        return new BackupPath(toStringWithUnixSeparator(path));
    }

    private BackupPath(final String path) {
        this.path = path;
    }

    @JsonValue
    public String toUri() {
        if (path.startsWith("/")) {
            return FILE_SCHEME_DOUBLE_SLASH + path;
        } else {
            return FILE_SCHEME_TRIPLE_SLASH + path;
        }
    }

    @Override
    public String toString() {
        return path;
    }

    /**
     * Converts the path to an OS path.
     *
     * @return the OS path
     */
    public Path toOsPath() {
        return Path.of(FilenameUtils.separatorsToSystem(path));
    }

    @Override
    public int compareTo(@NotNull final BackupPath o) {
        return PATH_COMPARATOR.compare(this, o);
    }

    /**
     * Determines if this path starts with the given backup path.
     *
     * @param testedPrefix the path we want to confirm whether this path starts with it
     * @return true if this path starts with the given backup path
     */
    public boolean startsWith(final BackupPath testedPrefix) {
        final String backupPathWithEndSeparator;
        if (testedPrefix.path.endsWith(UNIX_SEPARATOR)) {
            backupPathWithEndSeparator = testedPrefix.path;
        } else {
            backupPathWithEndSeparator = testedPrefix.path + UNIX_SEPARATOR;
        }
        return this.path.startsWith(backupPathWithEndSeparator);
    }

    /**
     * Returns the file name of this path.
     *
     * @return the file name
     */
    public String getFileName() {
        return FilenameUtils.getName(path);
    }

    /**
     * Returns the file represented by this path.
     *
     * @return the file
     */
    public File toFile() {
        return toOsPath().toFile();
    }

    /**
     * Returns the parent of this path.
     *
     * @return the parent
     */
    public BackupPath getParent() {
        final var fullPathNoEndSeparator = FilenameUtils.getFullPathNoEndSeparator(path);
        if (path.equals(fullPathNoEndSeparator)) {
            return null;
        }
        return new BackupPath(fullPathNoEndSeparator);
    }

    /**
     * Resolves a path relative to this path.
     *
     * @param child the name of the child we want to resolve
     * @return the resolved path
     */
    public BackupPath resolve(@NonNull final String child) {
        return BackupPath.ofPathAsIs(path + UNIX_SEPARATOR + child);
    }

    private static String toStringWithUnixSeparator(final Path path) {
        return normalizePath(path.toAbsolutePath().toString());
    }

    private static String normalizePath(final String path) {
        return FilenameUtils.normalize(FilenameUtils.separatorsToUnix(path), true);
    }
}
