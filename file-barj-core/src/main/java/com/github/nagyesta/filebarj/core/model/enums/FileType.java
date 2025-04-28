package com.github.nagyesta.filebarj.core.model.enums;

import lombok.Getter;
import lombok.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Represents the type of the file.
 */
public enum FileType {
    /**
     * Directory.
     */
    DIRECTORY(BasicFileAttributes::isDirectory, false),
    /**
     * Regular file.
     */
    REGULAR_FILE(BasicFileAttributes::isRegularFile, true) {
        @Override
        public InputStream streamContent(final Path path) throws IOException {
            return Files.newInputStream(path, StandardOpenOption.READ);
        }
    },
    /**
     * Symbolic link.
     */
    SYMBOLIC_LINK(BasicFileAttributes::isSymbolicLink, true) {
        @Override
        public InputStream streamContent(final Path path) throws IOException {
            final var linkedPathAsString = Files.readSymbolicLink(path).toString();
            return new ByteArrayInputStream(linkedPathAsString.getBytes(StandardCharsets.UTF_8));
        }
    },

    /**
     * Other (for example a device).
     */
    OTHER(BasicFileAttributes::isOther, false),

    /**
     * Missing files (used when the file does not exist at the time of parsing).
     */
    MISSING(Objects::isNull, false);

    private final Predicate<BasicFileAttributes> test;
    @Getter
    private final boolean contentSource;

    /**
     * Constructs an enum and sets the matching predicate.
     *
     * @param test          The matching predicate.
     * @param contentSource True if the content of the file is relevant for the archival.
     */
    FileType(final Predicate<BasicFileAttributes> test,
             final boolean contentSource) {
        this.test = test;
        this.contentSource = contentSource;
    }

    /**
     * Finds a suitable {@link FileType} based on the provided attributes.
     *
     * @param attributes The attributes.
     * @return The file type.
     */
    public static FileType findForAttributes(
            final @NonNull BasicFileAttributes attributes) {
        return Arrays.stream(values())
                .filter(f -> f.test.test(attributes))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find matching file type."));
    }

    /**
     * Opens a stream for accessing the useful content which should be archived during the backup.
     *
     * @param path The path of the file.
     * @return The stream of the file contents (if the {@link FileType#isContentSource()} is true).
     * @throws IOException                   When the stream cannot be opened.
     * @throws UnsupportedOperationException When {@link FileType#isContentSource()} is false.
     */
    public InputStream streamContent(final Path path) throws IOException {
        throw new UnsupportedOperationException("Content cannot be streamed for " + name());
    }
}
