package com.github.nagyesta.filebarj.core.util;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Utility class for logging operations.
 */
@UtilityClass
public class LogUtil {
    private static final String RESET = "\033[0;0m";
    private static final String RED = "\033[0;31m";

    /**
     * Makes the message more prominent by applying a red colour.
     *
     * @param message the message
     * @return the message with red colour
     */
    public static String scary(final String message) {
        return RED + message + RESET;
    }

    /**
     * Groups the files by their file type and calls the consumer for each group.
     *
     * @param ofFiles         the files
     * @param loggingConsumer the consumer
     */
    public static void logStatistics(
            final @NotNull Collection<FileMetadata> ofFiles,
            final @NotNull BiConsumer<FileType, Long> loggingConsumer) {
        countsByType(ofFiles).forEach(loggingConsumer);
    }

    /**
     * Groups the files by their file type and returns the counts.
     *
     * @param ofFiles the files
     * @return the counts of each file type
     */
    public static @NotNull TreeMap<FileType, Long> countsByType(
            final @NotNull Collection<FileMetadata> ofFiles) {
        return new TreeMap<>(ofFiles.stream()
                .collect(Collectors.groupingBy(FileMetadata::getFileType, Collectors.counting())));
    }
}
