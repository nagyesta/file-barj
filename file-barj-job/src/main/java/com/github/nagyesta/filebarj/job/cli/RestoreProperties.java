package com.github.nagyesta.filebarj.job.cli;

import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.validation.PastOrPresentEpochSeconds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.Map;

/**
 * The parsed command line arguments of a restore task.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RestoreProperties extends BackupFileProperties {
    @NonNull
    private final Map<BackupPath, Path> targets;
    @Positive
    private final int threads;
    private final boolean dryRun;
    private final boolean deleteFilesNotInBackup;
    @PastOrPresentEpochSeconds
    private final long pointInTimeEpochSeconds;
    @Valid
    private final BackupPath includedPath;
    private final PermissionComparisonStrategy permissionComparisonStrategy;
}
