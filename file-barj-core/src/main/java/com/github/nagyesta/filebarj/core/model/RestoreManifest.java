package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.model.enums.OperatingSystem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RestoreManifest extends EncryptionKeyStore {
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
     * The map of matching files identified during backup keyed by filename and Id.
     */
    private final @NonNull Map<String, Map<UUID, FileMetadata>> files;
    /**
     * The map of archive entries saved during backup keyed by filename and Id.
     */
    private final @NonNull Map<String, Map<UUID, ArchivedFileMetadata>> archivedEntries;

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
     * The map of all files in the manifest which are matching the provided predicate and their
     * parent directories. This is a read-only view of the files map.
     *
     * @param predicate the predicate filtering the paths to be returned
     * @return the map
     */
    public Map<UUID, FileMetadata> getFilesOfLastManifestFilteredBy(
            final Predicate<BackupPath> predicate) {
        final var filesOfLastManifest = getFilesOfLastManifest();
        final var allDirectories = filesOfLastManifest.values().stream()
                .filter(fileMetadata -> fileMetadata.getFileType() == FileType.DIRECTORY)
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toSet());
        final var foundPaths = filesOfLastManifest.values().stream()
                .map(FileMetadata::getAbsolutePath)
                .filter(predicate)
                .flatMap(path -> parents(allDirectories, path))
                .collect(Collectors.toSet());
        return filesOfLastManifest.entrySet().stream()
                .filter(e -> foundPaths.contains(e.getValue().getAbsolutePath()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The map of all files in the manifest which are matching the provided predicate and their
     * parent directories.. This is a read-only view of the files map.
     *
     * @param predicate the predicate filtering the paths to be returned
     * @return the list
     */
    public List<FileMetadata> getExistingContentSourceFilesOfLastManifestFilteredBy(
            final Predicate<BackupPath> predicate) {
        return getFilesOfLastManifest().values().stream()
                .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                .filter(fileMetadata -> fileMetadata.getFileType().isContentSource())
                .filter(fileMetadata -> predicate.test(fileMetadata.getAbsolutePath()))
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

    private Stream<BackupPath> parents(
            final Set<BackupPath> directories,
            final BackupPath path) {
        return Optional.ofNullable(path.getParent())
                .filter(directories::contains)
                .map(parent -> Stream.concat(Stream.of(path), parents(directories, parent)))
                .orElse(Stream.of(path));
    }
}
