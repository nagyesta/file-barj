package com.github.nagyesta.filebarj.core.model.enums;

import lombok.NonNull;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Represents the type of the file.
 */
public enum FileType {
    /**
     * Regular file.
     */
    REGULAR_FILE(BasicFileAttributes::isRegularFile),
    /**
     * Directory.
     */
    DIRECTORY(BasicFileAttributes::isDirectory),
    /**
     * Symbolic link.
     */
    SYMBOLIC_LINK(BasicFileAttributes::isSymbolicLink),
    /**
     * Other (for example a device).
     */
    OTHER(BasicFileAttributes::isOther);

    private final Predicate<BasicFileAttributes> test;

    /**
     * Constructs an enum and sets the matching predicate.
     *
     * @param test The matching predicate.
     */
    FileType(final Predicate<BasicFileAttributes> test) {
        this.test = test;
    }

    /**
     * Finds a suitable {@link FileType} based on the provided attributes.
     *
     * @param attributes The attributes.
     * @return The file type.
     */
    public static FileType findForAttributes(@NonNull final BasicFileAttributes attributes) {
        return Arrays.stream(values())
                .filter(f -> f.test.test(attributes))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find matching file type."));
    }
}
