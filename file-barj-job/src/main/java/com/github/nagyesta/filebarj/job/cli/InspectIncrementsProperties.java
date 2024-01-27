package com.github.nagyesta.filebarj.job.cli;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * The parsed command line arguments of a version inspection task.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class InspectIncrementsProperties extends BackupFileProperties {

}

