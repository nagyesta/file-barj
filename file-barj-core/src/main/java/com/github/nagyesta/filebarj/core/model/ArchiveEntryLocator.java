package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

/**
 * Provides a pointer identifying the location where the archived entry is stored.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ArchiveEntryLocator {
    /**
     * The backup increment containing the entry.
     */
    @JsonProperty("backup_increment")
    private final int backupIncrement;
    /**
     * The name of the entry (file) stored within the archive.
     */
    @NonNull
    @JsonProperty("entry_name")
    private final UUID entryName;
    /**
     * The random bytes used during encryption.
     */
    @JsonProperty("random_bytes")
    private final byte[] randomBytes;

}
