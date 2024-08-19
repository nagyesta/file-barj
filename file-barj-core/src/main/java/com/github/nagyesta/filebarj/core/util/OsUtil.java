package com.github.nagyesta.filebarj.core.util;

import com.github.nagyesta.filebarj.core.model.enums.OperatingSystem;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

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
        return getOs() == OperatingSystem.WINDOWS;
    }

    /**
     * Parses the current OS name.
     *
     * @return the current OS
     */
    public static @NotNull OperatingSystem getOs() {
        return OperatingSystem.forOsName(getRawOsName());
    }


    /**
     * Returns the raw OS name.
     *
     * @return the raw OS name
     */
    public static @NotNull String getRawOsName() {
        return System.getProperty("os.name");
    }
}
