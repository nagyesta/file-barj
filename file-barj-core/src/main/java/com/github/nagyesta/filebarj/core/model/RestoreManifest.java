package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
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
     * The file name prefix used by the backup archives as keys, mapped to the versions belonging to
     * that prefix.
     */
    @NonNull
    private final SortedMap<String, SortedSet<Integer>> fileNamePrefixes;
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

    /**
     * Returns the data decryption key for the given file name prefix using the private key for
     * decryption.
     *
     * @param fileNamePrefix the prefix
     * @param kekPrivateKey  the private key
     * @return the data decryption key
     */
    public @NotNull SecretKey dataIndexDecryptionKey(@NotNull final String fileNamePrefix, @NotNull final PrivateKey kekPrivateKey) {
        return dataIndexDecryptionKey(kekPrivateKey, fileNamePrefixes.get(fileNamePrefix).first());
    }

    protected RestoreManifest(final RestoreManifestBuilder<?, ?> builder) {
        super(builder);
        this.maximumAppVersion = builder.maximumAppVersion;
        this.lastStartTimeUtcEpochSeconds = builder.lastStartTimeUtcEpochSeconds;
        this.fileNamePrefixes = Collections.unmodifiableSortedMap(builder.fileNamePrefixes);
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
    public Map<UUID, FileMetadata> getFilesOfLastManifest() {
        return files.get(fileNamePrefixes.lastKey());
    }

    /**
     * The map of all files in the manifest. This is a read-only view of the files map.
     *
     * @return the map
     */
    public List<FileMetadata> getExistingContentSourceFilesOfLastManifest() {
        return getFilesOfLastManifest().values().stream()
                .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                .filter(fileMetadata -> fileMetadata.getFileType().isContentSource())
                .toList();
    }

    /**
     * The map of all archived entries from the latest manifest.
     *
     * @return the map
     */
    public Map<UUID, ArchivedFileMetadata> getArchivedEntriesOfLastManifest() {
        return archivedEntries.get(fileNamePrefixes.lastKey());
    }
}
