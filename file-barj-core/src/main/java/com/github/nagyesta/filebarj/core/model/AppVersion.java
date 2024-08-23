package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

/**
 * Represents an application version using semantic versioning.
 *
 * @param major Major version component
 * @param minor Minor version component
 * @param patch Patch version component
 */
public record AppVersion(
        @PositiveOrZero int major, @PositiveOrZero int minor, @PositiveOrZero int patch) implements Comparable<AppVersion> {

    /**
     * The version of the currently used file-barj component.
     */
    public static final String DEFAULT_VERSION = getDefaultVersion();

    /**
     * Parses the provided version and creates a new instance.
     *
     * @param version The version string using the major.minor.patch format e.g.: 1.2.3
     */
    @JsonCreator
    public AppVersion(final String version) {
        this(version.split("\\."));
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("The version number should be in major.minor.patch format e.g.: 1.2.3");
        }
    }

    /**
     * Constructs an instance with the default version.
     */
    public AppVersion() {
        this(DEFAULT_VERSION);
    }

    private AppVersion(final String[] versionTokens) {
        this(Integer.parseInt(versionTokens[0]), Integer.parseInt(versionTokens[1]), Integer.parseInt(versionTokens[2]));
    }

    /**
     * Formats the version into the String representation.
     *
     * @return The version string using the major.minor.patch format e.g.: 1.2.3
     */
    @JsonValue
    public String toJsonValue() {
        return major + "." + minor + "." + patch;
    }

    private static @NonNull String getDefaultVersion() {
        try (var stream = AppVersion.class.getResourceAsStream("/file-barj-component.version");
             var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8);
             var buffered = new LineNumberReader(reader)) {
            return buffered.readLine();
        } catch (final Exception e) {
            return "0.0.0";
        }
    }

    public int compareTo(final @NotNull AppVersion appVersion) {
        return Comparator.comparing(AppVersion::major)
                .thenComparing(AppVersion::minor)
                .thenComparing(AppVersion::patch)
                .compare(this, appVersion);
    }
}
