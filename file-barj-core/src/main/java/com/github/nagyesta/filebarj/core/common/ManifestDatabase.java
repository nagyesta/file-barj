package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupToOsMapper;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;

/**
 * Defines how data should be stored/accessed to/from manifests.
 */
@NotNullByDefault
public interface ManifestDatabase extends AutoCloseable {

    /**
     * Instantiates a new database using the default implementation.
     *
     * @return database
     */
    static ManifestDatabase newInstance() {
        return new InMemoryManifestDatabase();
    }

    //BACKUP INCREMENTS START
    SortedSet<ManifestId> getAllManifestIds();

    ManifestId persistIncrement(BackupIncrementManifest manifest);

    ManifestId createMergedIncrement(SortedSet<ManifestId> manifestsToMerge);

    boolean isEmpty();

    int nextIncrement();

    int size();

    void clear();

    String getLatestFileNamePrefix();

    String getFileNamePrefix(ManifestId current);

    SortedSet<Integer> getAllVersionIncrements();

    BackupJobConfiguration getLatestConfiguration();

    BackupJobConfiguration getConfiguration(ManifestId manifestId);
    //BACKUP INCREMENTS END

    //CRYPTO START
    @Nullable SecretKey getDataEncryptionKey(ArchiveEntryLocator archiveLocation);

    @Nullable SecretKey getLatestDataIndexEncryptionKey();

    SecretKey getDataIndexDecryptionKey(PrivateKey kek, ManifestId manifestId);

    SecretKey getDataDecryptionKey(PrivateKey kek, ArchiveEntryLocator entryName, ManifestId manifestId);
    //CRYPTO END

    //INCREMENTAL BACKUP OR BACKUP MERGE START
    void persistFileMetadata(ManifestId manifestId, FileMetadata metadata);

    void persistArchiveMetadata(ManifestId manifestId, ArchivedFileMetadata metadata);

    @Nullable ArchivedFileMetadata retrieveLatestArchiveMetadataByFileMetadataId(UUID fileId); //TODO: exclude currently built manifests

    @Nullable FileMetadata retrieveLatestFileMetadataBySourcePath(BackupPath sourcePath); //TODO: exclude currently built manifests

    SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalSizeBytes(Long originalSizeBytes); //TODO: exclude currently built manifests

    SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalHash(String originalHash); //TODO: exclude currently built manifests

    void setDataFileNames(ManifestId manifestId, List<String> dataFiles);

    void setIndexFileName(ManifestId manifestId, String indexFile);
    //INCREMENTAL BACKUP OR BACKUP MERGE END

    //BACKUP READ START
    ArchivedFileMetadata retrieveArchiveMetadata(int increment, UUID archiveId);

    SortedMap<FileType, Long> getFileStatistics(ManifestId manifest);

    @Deprecated
    BackupIncrementManifest get(ManifestId manifest);

    long totalCountOfArchiveEntries(ManifestId manifestId);

    Set<String> retrieveArchiveEntityPathsFor(ManifestId merged, ManifestId storageSource);

    List<FileMetadata> retrieveFilesFilteredBy(ManifestId manifestId, BackupPath includedPath, Collection<FileType> allowedTypes);

    long originalSizeOfFilesFilteredBy(ManifestId manifestId, BackupPath includedPath);

    Map<BackupPath, String> retrieveFileErrors(ManifestId manifestId);

    Set<BackupPath> retrieveAllPaths(ManifestId selectedIncrement);

    //FILE SET START
    UUID createFileSet();

    void persistFileSetItems(UUID fileSetId, Collection<Path> paths, BackupToOsMapper mapper);

    long countFileSetItems(UUID fileSetId);

    List<Path> retrieveFileWithCaseSensitivityIssues(UUID fileSetId);

    Map<FileType, Long> parseFileMetadataForFileSet(UUID fileSetId);
    //FILE SET END

    //FILES + RESTORE
    Set<Path> findFilesMissingFromBackupIncrement(ManifestId manifestId, UUID fileSetId);
    //FILES + RESTORE

    //CHANGE DETECTION START
    UUID detectChanges(ManifestId comparedToManifestId, UUID fileSetId, BackupToOsMapper pathMapper);

    SortedMap<Change, Long> getChangeStats(UUID changeSetId);

    boolean existsInLastIncrement(FileMetadata fileMetadata);
    //CHANGE DETECTION END

    Map<ArchiveEntryLocator, SortedSet<FileMetadata>> retrieveContentRestoreScopeForArchive(ManifestId restoredIncrement, ManifestId archiveManifestId, UUID changeSetId);
}
