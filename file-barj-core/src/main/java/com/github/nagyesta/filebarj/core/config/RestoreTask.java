package com.github.nagyesta.filebarj.core.config;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * Defines the parameters of a restore task.
 */
@Data
@Builder
public class RestoreTask {

    /**
     * Defines the target directories to restore files to.
     */
    @NonNull
    private final RestoreTargets restoreTargets;
    /**
     * The number of threads to use for parallel restore.
     */
    private final int threads;
    /**
     * Disallows file system changes when true.
     */
    private final boolean dryRun;
    /**
     * Allows deleting files from the restore targets which would have been in the backup scope but
     * are not in the backup increment when true.
     */
    private final boolean deleteFilesNotInBackup;
}
