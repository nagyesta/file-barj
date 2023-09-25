package com.github.nagyesta.filebarj.core.backup;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.FileMetadata;

import java.io.File;

/**
 * Parses metadata of Files.
 */
public interface FileMetadataParser {

    /**
     * Reads or calculates metadata of a file we need to include in the backup.
     * @param file The current {@link File} we need ot evaluate
     * @param configuration The backup configuration
     * @return the parsed {@link FileMetadata}
     */
    FileMetadata parse(File file, BackupJobConfiguration configuration);
}
