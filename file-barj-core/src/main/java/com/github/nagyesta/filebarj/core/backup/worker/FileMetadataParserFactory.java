package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.util.OsUtil;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for {@link FileMetadataParser} instances.
 */
@UtilityClass
public class FileMetadataParserFactory {

    /**
     * Returns an instance of {@link FileMetadataParser} for the current OS.
     *
     * @return the parser
     */
    public static @NotNull FileMetadataParser newInstance() {
        final var isWindows = OsUtil.isWindows();
        return newInstance(isWindows);
    }

    /**
     * Returns an instance of {@link FileMetadataParser} for the current OS.
     *
     * @param isWindows should be true if the current OS is Windows
     * @return the parser
     */
    static @NotNull PosixFileMetadataParser newInstance(final boolean isWindows) {
        if (isWindows) {
            return new WindowsFileMetadataParser();
        }
        return new PosixFileMetadataParser();
    }
}
