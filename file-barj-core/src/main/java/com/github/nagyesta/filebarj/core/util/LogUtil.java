package com.github.nagyesta.filebarj.core.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class for logging operations.
 */
@UtilityClass
public class LogUtil {
    private static final String RESET = "\033[0;0m";
    private static final String RED = "\033[0;31m";

    /**
     * Makes the message more prominent by applying a red colour.
     *
     * @param message the message
     * @return the message with red colour
     */
    public static String scary(final String message) {
        return RED + message + RESET;
    }
}
