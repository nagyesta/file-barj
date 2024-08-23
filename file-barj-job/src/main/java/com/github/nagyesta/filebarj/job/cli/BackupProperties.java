package com.github.nagyesta.filebarj.job.cli;

import jakarta.validation.constraints.Positive;
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
    private final @NonNull Path config;
    private final @Positive int threads;
    private final boolean forceFullBackup;
}
