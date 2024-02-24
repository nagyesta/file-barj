package com.github.nagyesta.filebarj.job.cli;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * The parsed command line arguments of a merge task.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MergeProperties extends BackupFileProperties {
    private final boolean deleteObsoleteFiles;
    private final long fromTimeEpochSeconds;
    private final long toTimeEpochSeconds;
}
