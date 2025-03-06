package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.common.database.ArchiveSetId;
import com.github.nagyesta.filebarj.core.common.database.ChangeSetId;
import com.github.nagyesta.filebarj.core.common.database.FileSetId;
import com.github.nagyesta.filebarj.core.common.database.InMemoryManifestDatabase;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupToOsMapper;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.function.Consumer;

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
    ArchivedFileMetadata retrieveArchiveMetadata(ArchiveEntryLocator archiveEntryLocator);

    SortedMap<FileType, Long> getFileStatistics(ManifestId manifest);

    @Deprecated
    BackupIncrementManifest get(ManifestId manifest);

    long totalCountOfArchiveEntries(ManifestId manifestId);

    Set<String> retrieveArchiveEntityPathsFor(ManifestId merged, ManifestId storageSource);

    FileSetId retrieveFilesFilteredBy(ManifestId manifestId, BackupPath includedPath, Collection<FileType> allowedTypes);

    long originalSizeOfFilesFilteredBy(ManifestId manifestId, BackupPath includedPath);

    Map<BackupPath, String> retrieveFileErrors(ManifestId manifestId);

    @Deprecated
    Set<BackupPath> retrieveAllPaths(ManifestId selectedIncrement);

    boolean backupContainsPath(ManifestId manifestId, String linkTargetPath);

    boolean backupContainsPath(ManifestId manifestId, Path osPath, BackupToOsMapper backupToOsMapper);

    //FILE SET START
    FileSetId createFileSet();

    default void doForEachPageOfPathsOrderedByPath(
            final FileSetId fileSetId,
            final int pageSize,
            final Consumer<List<BackupPath>> operation) {
        doForEachPageOfPathsOrderedByPath(fileSetId, pageSize, operation, dontClose -> {
        });
    }

    default void doForEachPageOfPathsOrderedByPath(
            final @NonNull FileSetId fileSetId,
            final int pageSize,
            final @NonNull Consumer<List<BackupPath>> operation,
            final @NonNull Consumer<FileSetId> closeOperation) {
        try {
            final var itemCount = countFileSetItems(fileSetId);
            for (var i = 0; i < itemCount; i += pageSize) {
                operation.accept(getNextPageOfFileSetItemBackupPaths(fileSetId, i, pageSize));
            }
        } finally {
            closeOperation.accept(fileSetId);
        }
    }

    default void doForEachPageOfFilesOrderedByPathAndCloseQuietly(
            final FileSetId fileSetId,
            final int pageSize,
            final Consumer<List<FileMetadata>> operation) {
        doForEachPageOfFilesOrderedByPath(fileSetId, pageSize, operation, IOUtils::closeQuietly);
    }

    default void doForEachPageOfFilesOrderedByPath(
            final FileSetId fileSetId,
            final int pageSize,
            final Consumer<List<FileMetadata>> operation) {
        doForEachPageOfFilesOrderedByPath(fileSetId, pageSize, operation, dontClose -> {
        });
    }

    default void doForEachPageOfFilesOrderedByPath(
            final @NonNull FileSetId fileSetId,
            final int pageSize,
            final @NonNull Consumer<List<FileMetadata>> operation,
            final @NonNull Consumer<FileSetId> closeOperation) {
        try {
            final var itemCount = countFileSetItems(fileSetId);
            for (var i = 0; i < itemCount; i += pageSize) {
                operation.accept(getNextPageOfFileMetadataSetItemsOrderByPath(fileSetId, i, pageSize));
            }
        } finally {
            closeOperation.accept(fileSetId);
        }
    }

    default void doForEachPageOfFilesReverseOrderedByPathAndCloseQuietly(
            final FileSetId fileSetId,
            final int pageSize,
            final Consumer<List<FileMetadata>> operation) {
        doForEachPageOfFilesReverseOrderedByPath(fileSetId, pageSize, operation, IOUtils::closeQuietly);
    }

    default void doForEachPageOfFilesReverseOrderedByPath(
            final @NonNull FileSetId fileSetId,
            final int pageSize,
            final @NonNull Consumer<List<FileMetadata>> operation,
            final @NonNull Consumer<FileSetId> closeOperation) {
        try {
            final var itemCount = countFileSetItems(fileSetId);
            for (var i = 0; i < itemCount; i += pageSize) {
                operation.accept(getNextPageOfFileMetadataSetItemsReverseOrderByPath(fileSetId, i, pageSize));
            }
        } finally {
            closeOperation.accept(fileSetId);
        }
    }

    default void doForEachPageOfFilesOrderedByHashAndCloseQuietly(
            final @NonNull FileSetId fileSetId,
            final int approxPageSize,
            final @NonNull Consumer<List<FileMetadata>> operation) {
        try {
            final var itemCount = countFileSetItems(fileSetId);
            var i = 0;
            while (i < itemCount) {
                final var approxList = getNextPageOfFileMetadataSetItemsOrderByHash(fileSetId, i, approxPageSize);
                if (approxList.isEmpty()) {
                    return;
                }
                final var allHashesAreTheSameOrMissing = approxList.stream()
                        .map(FileMetadata::getOriginalHash)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count() > 1;
                final List<FileMetadata> page;
                if (allHashesAreTheSameOrMissing || approxList.size() <= approxPageSize) {
                    page = approxList.stream().limit(approxPageSize).toList();
                } else {
                    final var lastHash = approxList.get(approxList.size() - 1).getOriginalHash();
                    page = approxList.stream()
                            .filter(file -> !Objects.equals(lastHash, file.getOriginalHash()))
                            .limit(approxPageSize)
                            .toList();
                }
                operation.accept(page);
                i += page.size();
            }
        } finally {
            IOUtils.closeQuietly(fileSetId);
        }
    }

    void persistFileSetItems(FileSetId fileSetId, Collection<Path> paths);

    void persistFileSetItems(FileSetId fileSetId, Collection<BackupPath> paths, BackupToOsMapper mapper);

    void persistParsedFileMetadataItemsForFileSet(FileSetId fileSetId, Collection<FileMetadata> fileMetadata);

    boolean isFileSetEmpty(FileSetId fileSetId);

    long countFileSetItems(FileSetId fileSetId);

    long sumContentSize(ManifestId manifestId, FileSetId fileSetId);

    long sumContentSize(FileSetId fileSetId);

    List<FileMetadata> getNextPageOfFileMetadataSetItemsOrderByPath(FileSetId fileSetId, int offset, int pageSize);

    List<FileMetadata> getNextPageOfFileMetadataSetItemsReverseOrderByPath(FileSetId fileSetId, int offset, int pageSize);

    List<FileMetadata> getNextPageOfFileMetadataSetItemsOrderByHash(FileSetId fileSetId, int offset, int pageSize);

    List<BackupPath> getNextPageOfFileSetItemBackupPaths(FileSetId fileSetId, int offset, int pageSize);

    List<Path> retrieveFileWithCaseSensitivityIssues(FileSetId fileSetId);

    void deleteFileSet(FileSetId fileSetId);
    //FILE SET END

    //FILES + RESTORE
    FileSetId retrieveFilesWithContentChanges(ChangeSetId changeSetId, FileSetId contentSourcesSetId);

    FileSetId retrieveFilesWithMetadataChanges(ChangeSetId changeSetId, FileSetId contentSourcesSetId);

    Map<ManifestId, ArchiveSetId> createRelevantRestoreScopeFor(ManifestId selectedIncrement, FileSetId filesWithContentChanges);

    long countArchiveSetItems(ArchiveSetId archiveSetId);

    long countArchiveSetFileItems(ArchiveSetId archiveSetId);

    Set<ArchiveEntryLocator> retrieveAllArchiveEntryLocators(ArchiveSetId archiveSetId);

    SortedSet<FileMetadata> retrieveFileMetadataInArchiveSetByLocator(
            ManifestId manifestId, ArchiveSetId archiveSetId, ArchiveEntryLocator locator);

    ArchiveSetId saveRestorePartition(
            ManifestId manifestId, ArchiveSetId archiveSetId, List<ArchiveEntryLocator> chunk);

    void deleteArchiveSet(ArchiveSetId archiveSetId);
    //FILES + RESTORE

    //CHANGE DETECTION START
    ChangeSetId createChangeSet();

    void persistChangeStatuses(ChangeSetId changeSetId, Map<BackupPath, Change> changeStatuses);

    Map<FileType, Long> getFileMetadataStatsForFileSet(FileSetId fileSetId);

    SortedMap<Change, Long> getChangeStats(ChangeSetId changeSetId);

    boolean existsInLastIncrement(FileMetadata fileMetadata);

    void deleteChangeSet(ChangeSetId changeSetId);

    Optional<Change> retrieveChange(ChangeSetId changeSetId, BackupPath fileAbsolutePath);
    //CHANGE DETECTION END
}
