package com.github.nagyesta.filebarj.job.cli;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.nio.file.Path;

/**
 * The parsed command line arguments of a backup task.
 */
@Data
@Builder
public class BackupProperties {
    @NonNull
    private final Path config;
    private final int threads;
    private final boolean forceFullBackup;
}
