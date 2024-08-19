package com.github.nagyesta.filebarj.core.config.enums;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;

import java.util.function.Function;

/**
 * Defines the strategy used in case a file is found in more than one place.
 */
public enum DuplicateHandlingStrategy {
    /**
     * Archives each copies as separate entry in the archive.
     * <br/>e.g.,<br/>
     * Each duplicate is added as many times as it is found in the source.
     */
    KEEP_EACH,
    /**
     * Archives one copy per any increment of the backup since the last full backup.
     * <br/>e.g.,<br/>
     * The file is not added to the current archive even if the duplicate is found archived in a
     * previous backup version, such as a file was overwritten with a previously archived version
     * of the same file,
     */
    KEEP_ONE_PER_BACKUP {

        @Override
        public Function<FileMetadata, String> fileGroupingFunctionForHash(final @NonNull HashAlgorithm hashAlgorithm) {
            return hashAlgorithm.fileGroupingFunction();
        }
    };

    /**
     * Returns the file metadata grouping function for the specified hash algorithm. The grouping
     * function is used to form groups containing the files with the same content in the backup.
     *
     * @param hashAlgorithm the hash algorithm
     * @return the grouping function
     */
    public Function<FileMetadata, String> fileGroupingFunctionForHash(final @NonNull HashAlgorithm hashAlgorithm) {
        return fileMetadata -> fileMetadata.getId().toString();
    }
}
