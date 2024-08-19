package com.github.nagyesta.filebarj.core.config;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Set;

/**
 * Wraps the {@link RestoreTarget} entries in a single object.
 *
 * @param restoreTargets the restore targets
 */
public record RestoreTargets(@NonNull Set<RestoreTarget> restoreTargets) {

    /**
     * Converts the original path to the restore path.
     *
     * @param originalPath the original path
     * @return the restore path
     */
    public @NotNull Path mapToRestorePath(final @NonNull BackupPath originalPath) {
        return restoreTargets.stream()
                .filter(restoreTarget -> restoreTarget.matchesArchivedFile(originalPath))
                .findFirst()
                .map(filePath -> filePath.mapBackupPathToRestorePath(originalPath))
                .orElse(originalPath.toOsPath());
    }
}
