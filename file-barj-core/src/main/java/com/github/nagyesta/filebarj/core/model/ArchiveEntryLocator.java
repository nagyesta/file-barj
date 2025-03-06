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

import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provides a pointer identifying the location where the archived entry is stored.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ArchiveEntryLocator implements Comparable<ArchiveEntryLocator> {
    private static final String INCREMENT = "increment";
    private static final String NAME = "name";
    private static final Pattern PATH_REGEX = Pattern.compile(
            "^/(?<increment>\\d+)/(?<name>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");
    private static final Comparator<ArchiveEntryLocator> COMPARATOR = Comparator.comparing(ArchiveEntryLocator::asEntryPath);
    /**
     * The backup increment containing the entry.
     */
    @JsonProperty("backup_increment")
    private final @PositiveOrZero int backupIncrement;
    /**
     * The name of the entry (file) stored within the archive.
     */
    @JsonProperty("entry_name")
    private final @NonNull UUID entryName;

    /**
     * Returns the path to the entry.
     *
     * @return the path
     */
    @JsonIgnore
    public @NotNull String asEntryPath() {
        return String.format("/%s/%s", backupIncrement, entryName);
    }

    /**
     * Parses the given entry path.
     *
     * @param entryPath the path
     * @return the locator
     */
    public static ArchiveEntryLocator fromEntryPath(final @NonNull String entryPath) {
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

    @Override
    public int compareTo(@NonNull final ArchiveEntryLocator o) {
        return COMPARATOR.compare(this, o);
    }
}
