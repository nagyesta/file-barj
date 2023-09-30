package com.github.nagyesta.filebarj.core.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class for OS specific operations.
 */
@UtilityClass
public final class OsUtil {


    /**
     * Returns true if the OS is Windows.
     *
     * @return true if the OS is Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("win");
    }
}
