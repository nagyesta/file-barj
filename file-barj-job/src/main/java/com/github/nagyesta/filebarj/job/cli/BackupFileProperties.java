package com.github.nagyesta.filebarj.job.cli;

import com.github.nagyesta.filebarj.core.validation.FileNamePrefix;
import jakarta.validation.Valid;
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
    @Valid
    private final KeyStoreProperties keyProperties;
    @FileNamePrefix
    @NonNull
    private final String prefix;
}
