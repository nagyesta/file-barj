package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.enums.OperatingSystem;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.io.Closeable;
import java.security.PrivateKey;
import java.util.*;
import java.util.stream.Stream;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RestoreManifest extends EncryptionKeyStore implements Closeable {
    /**
     * The version number of the app that generated the manifest.
     */
    private final @NonNull AppVersion maximumAppVersion;
    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    private final long lastStartTimeUtcEpochSeconds;
    /**
     * The file name prefix used by the backup archives as keys, mapped to the versions belonging to
     * that prefix.
     */
    private final @NonNull SortedMap<String, SortedSet<Integer>> fileNamePrefixes;
    /**
     * The snapshot of the backup configuration at the time of backup.
     */
    private final @NonNull BackupJobConfiguration configuration;
    private final @NonNull OperatingSystem operatingSystem;
    /**
     * The key to the matching files identified during backup.
     */
    private final @NonNull FileMetadataSetId files;
    /**
     * The key to the archive entries saved during backup.
     */
    private final @NonNull ArchivedFileMetadataSetId archivedEntries;

    /**
     * Returns the data decryption key for the given file name prefix using the private key for
     * decryption.
     *
     * @param fileNamePrefix the prefix
     * @param kekPrivateKey  the private key
     * @return the data decryption key
     */
    public @NotNull SecretKey dataIndexDecryptionKey(
            final @NotNull String fileNamePrefix,
            final @NotNull PrivateKey kekPrivateKey) {
        return dataIndexDecryptionKey(kekPrivateKey, fileNamePrefixes.get(fileNamePrefix).first());
    }

    protected RestoreManifest(final RestoreManifestBuilder<?, ?> builder) {
        super(builder);
        this.maximumAppVersion = builder.maximumAppVersion;
        this.lastStartTimeUtcEpochSeconds = builder.lastStartTimeUtcEpochSeconds;
        this.fileNamePrefixes = Collections.unmodifiableSortedMap(builder.fileNamePrefixes);
        this.configuration = builder.configuration;
        this.operatingSystem = builder.operatingSystem;
        this.files = builder.files;
        this.archivedEntries = builder.archivedEntries;
    }

    /**
     * The key of all files in the manifest. This is a read-only view of the files map.
     *
     * @return the map
     */
    public FileMetadataSetId getFilesOfLastManifest() {
        return files;
    }

    /**
     * The key of all archived entries from the latest manifest.
     *
     * @return the map
     */
    public ArchivedFileMetadataSetId getArchivedEntriesOfLastManifest() {
        return archivedEntries;
    }

    private Stream<BackupPath> parents(
            final Set<BackupPath> directories,
            final BackupPath path) {
        return Optional.ofNullable(path.getParent())
                .filter(directories::contains)
                .map(parent -> Stream.concat(Stream.of(path), parents(directories, parent)))
                .orElse(Stream.of(path));
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(this.files);
        IOUtils.closeQuietly(this.archivedEntries);
    }
}
