package com.github.nagyesta.filebarj.job.cli;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.Map;

/**
 * The parsed command line arguments of a restore task.
 */
@Data
@Builder
public class RestoreProperties {
    @NonNull
    private final Path backupSource;
    private final KeyStoreProperties keyProperties;
    @NonNull
    private final String prefix;
    @NonNull
    private final Map<Path, Path> targets;
    private final int threads;
    private final boolean dryRun;
    private final boolean deleteFilesNotInBackup;
}
