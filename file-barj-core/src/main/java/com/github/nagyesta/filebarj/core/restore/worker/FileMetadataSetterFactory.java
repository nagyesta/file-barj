package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.util.OsUtil;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for {@link FileMetadataSetter} instances.
 */
@UtilityClass
public class FileMetadataSetterFactory {

    /**
     * Creates a new instance for the specified root path. The instance will be suitable for the
     * current OS.
     *
     * @param restoreTargets the mappings of the root paths where we would like to restore
     * @return a metadata setter
     */
    public static FileMetadataSetter newInstance(@NotNull final RestoreTargets restoreTargets) {
        return newInstance(restoreTargets, OsUtil.isWindows());
    }

    /**
     * Creates a new instance for the specified root path. The instance will be suitable for the
     * current OS.
     *
     * @param restoreTargets the mappings of the root paths where we would like to restore
     * @param isWindows      should be true if the current OS is Windows
     * @return a metadata setter
     */
    @NotNull
    static PosixFileMetadataSetter newInstance(
            @NonNull final RestoreTargets restoreTargets, final boolean isWindows) {
        if (isWindows) {
            return new WindowsFileMetadataSetter(restoreTargets);
        }
        return new PosixFileMetadataSetter(restoreTargets);
    }
}
