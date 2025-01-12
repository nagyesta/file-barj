package com.github.nagyesta.filebarj.core.config;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

/**
 * Converter for backup path to OS path conversions.
 */
@NotNullByDefault
public interface BackupToOsMapper {

    /**
     * Maps backup path to OS path without any changes.
     */
    BackupToOsMapper IDENTITY = BackupPath::toOsPath;

    /**
     * Converts the backup path to the OS path.
     *
     * @param backupPath the backup path
     * @return the OS path
     */
    Path mapToOsPath(BackupPath backupPath);
}
