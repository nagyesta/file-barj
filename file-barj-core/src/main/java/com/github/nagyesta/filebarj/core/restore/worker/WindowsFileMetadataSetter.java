package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Windows implementation of {@link FileMetadataSetter}.
 */
public class WindowsFileMetadataSetter extends PosixFileMetadataSetter {

    /**
     * Creates a new instance for the specified root path.
     *
     * @param restoreTargets     the mappings of the root paths where we would like to restore
     * @param permissionStrategy the permission comparison strategy
     */
    public WindowsFileMetadataSetter(
            @NotNull final RestoreTargets restoreTargets,
            @Nullable final PermissionComparisonStrategy permissionStrategy) {
        super(restoreTargets, permissionStrategy);
    }

    @Override
    public void setOwnerAndGroup(@NotNull final FileMetadata metadata) {
        //no-op
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void doSetPermissions(
            @NotNull final Path filePath,
            @NotNull final Set<PosixFilePermission> posixFilePermissions) {
        performIoTaskAndHandleException(() -> {
            final var file = filePath.toFile();
            file.setExecutable(posixFilePermissions.contains(PosixFilePermission.OWNER_EXECUTE));
            file.setReadable(posixFilePermissions.contains(PosixFilePermission.OWNER_READ));
            file.setWritable(posixFilePermissions.contains(PosixFilePermission.OWNER_WRITE));
            return null;
        });
    }

    @Override
    public void setHiddenStatus(@NonNull final FileMetadata metadata) {
        if (metadata.getFileType() == FileType.SYMBOLIC_LINK) {
            return;
        }
        final var absolutePath = getRestoreTargets().mapToRestorePath(metadata.getAbsolutePath());
        performIoTaskAndHandleException(() -> {
            if (metadata.getHidden()) {
                Runtime.getRuntime().exec(new String[]{"attrib", "+H", absolutePath.toString()}).waitFor();
            }
            return null;
        });
    }
}
