package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provides a pointer identifying the location where the archived entry is stored.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ArchiveEntryLocator {
    private static final String INCREMENT = "increment";
    private static final String NAME = "name";
    private static final Pattern PATH_REGEX = Pattern.compile(
            "^/(?<increment>\\d+)/(?<name>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");
    /**
     * The backup increment containing the entry.
     */
    @PositiveOrZero
    @JsonProperty("backup_increment")
    private final int backupIncrement;
    /**
     * The name of the entry (file) stored within the archive.
     */
    @NonNull
    @JsonProperty("entry_name")
    private final UUID entryName;

    /**
     * Returns the path to the entry.
     *
     * @return the path
     */
    @JsonIgnore
    @NotNull
    public String asEntryPath() {
        return String.format("/%s/%s", backupIncrement, entryName);
    }

    /**
     * Parses the given entry path.
     *
     * @param entryPath the path
     * @return the locator
     */
    public static ArchiveEntryLocator fromEntryPath(@NonNull final String entryPath) {
        final var matcher = PATH_REGEX.matcher(entryPath);
        if (matcher.matches()) {
            return ArchiveEntryLocator.builder()
                    .backupIncrement(Integer.parseInt(matcher.group(INCREMENT)))
                    .entryName(UUID.fromString(matcher.group(NAME)))
                    .build();
        } else {
            return null;
        }
    }

}
