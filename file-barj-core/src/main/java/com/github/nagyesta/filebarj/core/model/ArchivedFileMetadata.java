package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.util.HashSet;
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
     * The hash of the archived content.
     */
    @JsonProperty("archived_hash")
    private String archivedHash;
    /**
     * The hash of the original content.
     */
    @JsonProperty("original_hash")
    private String originalHash;
    /**
     * The Ids of the original files which are archived by the current entry. If multiple Ids are
     * listed, then duplicates where eliminated.
     */
    @NonNull
    @JsonProperty("files")
    private Set<UUID> files;

    /**
     * Copies the metadata except the Id and the files.
     * The Id is replaced with a random one and the files are cleared.
     *
     * @return The new instance
     */
    @JsonIgnore
    public ArchivedFileMetadata copyArchiveDetails() {
        return ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .archiveLocation(archiveLocation)
                .archivedHash(archivedHash)
                .originalHash(originalHash)
                .files(new HashSet<>())
                .build();
    }
}
