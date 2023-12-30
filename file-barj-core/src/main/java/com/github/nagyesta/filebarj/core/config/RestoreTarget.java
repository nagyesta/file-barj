package com.github.nagyesta.filebarj.core.config;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Represents a restore source root. Can match a file or directory.
 *
 * @param backupPath  the original path used as backup source
 * @param restorePath the restore path where the files should be restored from this source
 */
public record RestoreTarget(Path backupPath, Path restorePath) {

    /**
     * Evaluates whether the given path is equal to or is a child of the backup path.
     *
     * @param archivedFilePath the original path of a file which was archived during the backup
     * @return whether the given path matches the backup path
     */
    public boolean matchesArchivedFile(@NonNull final Path archivedFilePath) {
        return archivedFilePath.equals(backupPath) || archivedFilePath.startsWith(backupPath);
    }

    /**
     * Maps the original file path from the backup to the restore path.
     *
     * @param filePath the original path
     * @return the mapped path where the file should be restored to
     */
    public Path mapBackupPathToRestorePath(@NotNull final Path filePath) {
        if (!matchesArchivedFile(filePath)) {
            throw new IllegalArgumentException("The given path is not a child of the backup path");
        }
        final var absoluteOriginalAsString = filePath.toAbsolutePath().toString();
        final var backupPathAsString = this.backupPath.toAbsolutePath().toString();
        final var relative = absoluteOriginalAsString.substring(backupPathAsString.length());
        return Path.of(restorePath.toAbsolutePath().toString(), relative);
    }

    @Override
    public String toString() {
        return "RestoreTarget{backupPath=" + backupPath + ", restorePath=" + restorePath + "}";
    }
}
