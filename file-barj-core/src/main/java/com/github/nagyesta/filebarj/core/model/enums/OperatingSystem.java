package com.github.nagyesta.filebarj.core.model.enums;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Represents the current OS.
 */
public enum OperatingSystem {
    /**
     * Windows.
     */
    WINDOWS("win"),
    /**
     * Mac OS.
     */
    MAC("mac"),
    /**
     * Linux.
     */
    LINUX("linux"),
    /**
     * Catch-all for any OS not matching the above.
     */
    UNKNOWN(null);

    private final String nameToken;

    /**
     * Creates a new instance.
     *
     * @param nameToken the OS name token we need to search for in the OS name.
     */
    OperatingSystem(final String nameToken) {
        this.nameToken = nameToken;
    }

    /**
     * Parses the OS name.
     *
     * @param name the OS name
     * @return the matching constant or UNKNOWN
     */
    public static @NotNull OperatingSystem forOsName(final @Nullable String name) {
        final var osName = Optional.ofNullable(name)
                .map(String::toLowerCase)
                .orElse("unknown");
        return Arrays.stream(values())
                .filter(os -> os.nameToken != null && osName.contains(os.nameToken))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
