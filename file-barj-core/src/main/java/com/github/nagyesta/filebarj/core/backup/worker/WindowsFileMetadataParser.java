package com.github.nagyesta.filebarj.core.backup.worker;

import java.io.File;

/**
 * Windows specific implementation of the {@link FileMetadataParser}.
 */
public class WindowsFileMetadataParser extends PosixFileMetadataParser {

    @Override
    protected Permissions posixPermissions(final File file) {
        return performIoTaskAndHandleException(
                () -> new Permissions(file.canRead(), file.canWrite(), file.canExecute()));
    }
}
