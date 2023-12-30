package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RestoreManifest extends EncryptionKeyStore {
    /**
     * The version number of the app that generated the manifest.
     */
    @NonNull
    private final AppVersion maximumAppVersion;
    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    private final long lastStartTimeUtcEpochSeconds;
    /**
     * The file name prefix used by the backup archives.
     */
    @NonNull
    private final SortedSet<String> fileNamePrefixes;
    /**
     * The snapshot of the backup configuration at the time of backup.
     */
    @NonNull
    private final BackupJobConfiguration configuration;
    /**
     * The map of matching files identified during backup keyed by filename and Id.
     */
    @NonNull
    private final Map<String, Map<UUID, FileMetadata>> files;
    /**
     * The map of archive entries saved during backup keyed by filename and Id.
     */
    @NonNull
    private final Map<String, Map<UUID, ArchivedFileMetadata>> archivedEntries;

    protected RestoreManifest(final RestoreManifestBuilder<?, ?> builder) {
        super(builder);
        this.maximumAppVersion = builder.maximumAppVersion;
        this.lastStartTimeUtcEpochSeconds = builder.lastStartTimeUtcEpochSeconds;
        this.fileNamePrefixes = Collections.unmodifiableSortedSet(builder.fileNamePrefixes);
        this.configuration = builder.configuration;
        this.files = builder.files.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Collections.unmodifiableMap(entry.getValue())));
        this.archivedEntries = builder.archivedEntries.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Collections.unmodifiableMap(entry.getValue())));
    }

    /**
     * The map of all files in the manifest. This is a read-only view of the files map.
     *
     * @return the map
     */
    public Map<UUID, FileMetadata> allFilesReadOnly() {
        return files.values().stream()
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The map of all archived entries in the manifest. This is a read-only view of the map.
     *
     * @return the map
     */
    public Map<UUID, ArchivedFileMetadata> allArchivedEntriesReadOnly() {
        return archivedEntries.values().stream()
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
