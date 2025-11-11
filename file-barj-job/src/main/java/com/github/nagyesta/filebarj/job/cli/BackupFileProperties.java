package com.github.nagyesta.filebarj.job.cli;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    private final @NonNull Path backupSource;
    private final @Valid KeyStoreProperties keyProperties;
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    @NotBlank
    private final @NonNull String prefix;
}
