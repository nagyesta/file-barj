package com.github.nagyesta.filebarj.job.cli;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * The parsed command line arguments of a version deletion task.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DeleteIncrementsProperties extends BackupFileProperties {
    @Positive
    private final long afterEpochSeconds;
}

