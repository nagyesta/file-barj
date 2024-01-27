package com.github.nagyesta.filebarj.job.cli;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;

/**
 * The parsed command line arguments of a generic task using backup files.
 */
@Data
@SuperBuilder
public class BackupFileProperties {
    @NonNull
    private final Path backupSource;
    private final KeyStoreProperties keyProperties;
    @NonNull
    private final String prefix;
}
