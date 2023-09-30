package com.github.nagyesta.filebarj.core.backup.service;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;

import java.nio.file.Path;
import java.util.List;

/**
 * The service providing the entry point for backup operations.
 */
public interface BackupService {
    /**
     * Reads the backup configuration from the provided location.
     *
     * @param configurationFile The file system location of the configuration
     * @return The parsed configuration
     */
    BackupJobConfiguration readJobConfiguration(Path configurationFile);

    /**
     * Performs the backup which is described in the provided configuration.
     *
     * @param configuration The backup configuration
     * @return The list of files created during the backup process
     */
    List<Path> performBackup(BackupJobConfiguration configuration);
}
