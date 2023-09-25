package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;
import java.util.UUID;

/**
 * Contains information about an archived entry.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchivedFileMetadata {
    /**
     * The unique Id of the metadata record.
     */
    @NonNull
    @JsonProperty("id")
    private final UUID id;
    /**
     * The location where the archived file contents are stored.
     */
    @NonNull
    @JsonProperty("archive_location")
    private final ArchiveEntryLocator archiveLocation;
    /**
     * The checksum of the archived content.
     */
    @JsonProperty("archived_checksum")
    private String archivedChecksum;
    /**
     * The checksum of the original content.
     */
    @JsonProperty("original_checksum")
    private String originalChecksum;
    /**
     * The Ids of the original files which are archived by the
     * current entry. If multiple Ids are listed, then duplicates
     * where eliminated.
     */
    @NonNull
    @JsonProperty("files")
    private Set<UUID> files;
}
