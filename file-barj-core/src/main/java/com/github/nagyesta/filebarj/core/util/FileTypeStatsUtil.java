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
 * Utility class for file type statistics logging operations.
 */
@UtilityClass
public class FileTypeStatsUtil {

    /**
     * Groups the files by their file type and calls the consumer for each group.
     *
     * @param ofFiles         the files
     * @param loggingConsumer the consumer
     */
    public static void logStatistics(
            @NotNull final Collection<FileMetadata> ofFiles,
            @NotNull final BiConsumer<FileType, Long> loggingConsumer) {
        countsByType(ofFiles).forEach(loggingConsumer);
    }

    /**
     * Groups the files by their file type and returns the counts.
     *
     * @param ofFiles the files
     * @return the counts of each file type
     */
    @NotNull
    public static TreeMap<FileType, Long> countsByType(
            @NotNull final Collection<FileMetadata> ofFiles) {
        return new TreeMap<>(ofFiles.stream()
                .collect(Collectors.groupingBy(FileMetadata::getFileType, Collectors.counting())));
    }
}
