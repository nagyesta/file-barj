package com.github.nagyesta.filebarj.core.config;

import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Defines the parameters of a restore task.
 */
@Data
@Builder
public class RestoreTask {

    /**
     * Defines the target directories to restore files to.
     */
    private final @NonNull RestoreTargets restoreTargets;
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
    /**
     * The root path of the backup entries (directory or file) to include during the restore.
     */
    private final BackupPath includedPath;
    /**
     * The strategy to use when comparing permissions.
     * <br/>
     * Defaults to {@link PermissionComparisonStrategy#STRICT}.
     */
    @Builder.Default
    private final PermissionComparisonStrategy permissionComparisonStrategy = PermissionComparisonStrategy.STRICT;

    /**
     * Returns the path filter for this restore task based on the included path.
     *
     * @return the path filter
     */
    public Predicate<BackupPath> getPathFilter() {
        return Optional.ofNullable(includedPath)
                .map(inclPath -> (Predicate<BackupPath>) path -> path.equals(inclPath) || path.startsWith(inclPath))
                .orElse(path -> true);
    }
}
