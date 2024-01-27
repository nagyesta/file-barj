package com.github.nagyesta.filebarj.job.cli;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;

/**
 * The parsed command line arguments of an increment content inspection task.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class InspectIncrementContentsProperties extends BackupFileProperties {

    private final long pointInTimeEpochSeconds;

    @NonNull
    private final Path outputFile;
}
