package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * Defines how data should be stored/accessed to/from manifests.
 */
public interface ManifestDatabase extends AutoCloseable {

    /**
     * Instantiates a new database using the default implementation.
     * @return database
     */
    static @NotNull ManifestDatabase newInstance() {
        return new InMemoryManifestDatabase();
    }

    ManifestId persistIncrement(BackupIncrementManifest manifest);

    void persistFileMetadata(ManifestId manifestId, FileMetadata metadata);

    void persistArchiveMetadata(ManifestId manifestId, ArchivedFileMetadata metadata);

    FileMetadata retrieveFileMetadata(int increment, UUID fileId);

    FileMetadata retrieveFileMetadata(String filenamePrefix, UUID fileId);

    ArchivedFileMetadata retrieveArchiveMetadata(int increment, UUID archiveId);

    ArchivedFileMetadata retrieveArchiveMetadata(ArchiveEntryLocator archiveLocation);

    ArchivedFileMetadata retrieveArchiveMetadata(String filenamePrefix, UUID archiveId);

    Set<FileMetadata> retrieveFileMetadataForArchive(int increment, UUID archiveId);

    Set<FileMetadata> retrieveFileMetadataForArchive(String filenamePrefix, UUID archiveId);

    Optional<ArchivedFileMetadata> retrieveLatestArchiveMetadataByFileMetadataId(UUID fileId);

    FileMetadata retrieveLatestFileMetadataBySourcePath(BackupPath sourcePath);

    SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalSizeBytes(Long originalSizeBytes);

    SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalHash(String originalHash);

    boolean existsInLastIncrement(FileMetadata fileMetadata);

    boolean isEmpty();

    int nextIncrement();

    int size();

    BackupJobConfiguration getLatestConfiguration();

    SortedMap<FileType, Long> getFileStatistics(ManifestId manifest);

    void setDataFileNames(ManifestId manifestId, List<String> dataFiles);

    void setIndexFileName(ManifestId manifestId, String indexFile);

    @Deprecated
    BackupIncrementManifest get(ManifestId manifest);

    SortedSet<Integer> getAllVersionIncrements();

    SecretKey getDataEncryptionKey(ArchiveEntryLocator archiveLocation);

    String getLatestFileNamePrefix();

    SecretKey getLatestDataIndexEncryptionKey();
}
