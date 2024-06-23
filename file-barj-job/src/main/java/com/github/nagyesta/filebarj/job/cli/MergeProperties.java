package com.github.nagyesta.filebarj.job.cli;

import com.github.nagyesta.filebarj.core.validation.PastOrPresentEpochSeconds;
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
    @PastOrPresentEpochSeconds
    private final long fromTimeEpochSeconds;
    @PastOrPresentEpochSeconds
    private final long toTimeEpochSeconds;
}
