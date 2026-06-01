package com.github.nagyesta.filebarj.core.persistence.h2.extension;

import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.FileMetadataIndex;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.GroupedIdCollection;
import com.github.nagyesta.filebarj.core.persistence.h2.mapper.*;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.*;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.github.nagyesta.filebarj.core.persistence.DataStore.BATCH_CHUNK_SIZE;

@Transaction(value = TransactionIsolationLevel.READ_UNCOMMITTED)
public interface H2FileMetadataSetRepositoryExtension
        extends H2BaseRepositoryExtension<FileMetadataSetId, UUID, FileMetadata> {

    @SqlUpdate("""
                DELETE FROM FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    void deleteAll(@BindMethods("id") FileMetadataSetId id);

    @SqlUpdate("""
                DROP TABLE FILE_METADATA
            """)
    @Override
    void dropAll();

    @SqlUpdate("""
                DELETE FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND ID = :fileId
            """)
    @Override
    void deleteOne(
            @BindMethods("id") FileMetadataSetId id,
            @Bind("fileId") UUID fileId);

    @SqlQuery("""
                SELECT COUNT(*) > 0
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    boolean exists(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    @UseRowMapper(H2FileMetadataRowMapper.class)
    ResultIterable<FileMetadata> fetch(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                  ORDER BY ABSOLUTE_PATH ASC
            """)
    @Override
    @UseRowMapper(H2FileMetadataRowMapper.class)
    ResultIterable<FileMetadata> fetchAsc(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                  ORDER BY ABSOLUTE_PATH DESC
            """)
    @Override
    @UseRowMapper(H2FileMetadataRowMapper.class)
    ResultIterable<FileMetadata> fetchDesc(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    long countAll(@BindMethods("id") FileMetadataSetId id);

    @SqlBatch("""
                INSERT INTO FILE_METADATA (
                    SET_ID,
                    ID,
                    FILE_SYSTEM_KEY,
                    ABSOLUTE_PATH,
                    ORIGINAL_HASH,
                    ORIGINAL_SIZE_BYTES,
                    ORIGINAL_HASH_AND_SIZE,
                    PATH_AND_SIZE_AND_LAST_MODIFIED,
                    LAST_MODIFIED,
                    LAST_ACCESSED,
                    CREATED,
                    POSIX_PERMISSIONS,
                    OWNER_NAME,
                    GROUP_NAME,
                    FILE_TYPE,
                    HIDDEN,
                    STATUS,
                    ARCHIVE_ID,
                    ERROR
                  ) VALUES (
                    :id.id,
                    :entity.id,
                    :entity.fileSystemKey,
                    :entity.absolutePath.uri,
                    :entity.originalHash,
                    :entity.originalSizeBytes,
                    CONCAT(:entity.originalHash, '_', :entity.originalSizeBytes),
                    CONCAT(:entity.absolutePath.uri, '_', :entity.originalSizeBytes, '_', :entity.lastModifiedUtcEpochSeconds),
                    :entity.lastModifiedUtcEpochSeconds,
                    :entity.lastAccessedUtcEpochSeconds,
                    :entity.createdUtcEpochSeconds,
                    :entity.posixPermissions,
                    :entity.owner,
                    :entity.group,
                    :entity.fileType,
                    :entity.hidden,
                    :entity.status,
                    :entity.archiveMetadataId,
                    :entity.error
                  )
            """)
    @BatchChunkSize(BATCH_CHUNK_SIZE)
    @Override
    void appendTo(
            @BindMethods("id") FileMetadataSetId id,
            @BindBean("entity") Collection<FileMetadata> entity);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
                  ORDER BY ABSOLUTE_PATH ASC
            """)
    @UseRowMapper(H2FileMetadataRowMapper.class)
    ResultIterable<FileMetadata> fetchByChangeStatusesAndFileTypesAsc(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
                  ORDER BY ABSOLUTE_PATH DESC
            """)
    @UseRowMapper(H2FileMetadataRowMapper.class)
    ResultIterable<FileMetadata> fetchByChangeStatusesAndFileTypesDesc(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND FILE_TYPE IN (<fileTypes>)
            """)
    long countByType(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
            """)
    long countByChangeStatusesAndTypes(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT
                    FILE_TYPE AS fileType,
                    COUNT(*) AS number
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                  GROUP BY FILE_TYPE
            """)
    @UseRowReducer(H2FileTypeStatisticsSortedMapReducer.class)
    SortedMap<FileType, Long> countsByType(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT
                    STATUS AS status,
                    COUNT(*) AS number
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                  GROUP BY STATUS
            """)
    @UseRowReducer(H2ChangeStatisticsSortedMapReducer.class)
    SortedMap<Change, Long> countsByStatus(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT COUNT(DISTINCT ORIGINAL_HASH_AND_SIZE)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
            """)
    long countDuplicateKeysByHashOf(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT COUNT(DISTINCT PATH_AND_SIZE_AND_LAST_MODIFIED)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
            """)
    long countDuplicateKeysByTimestampOf(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT
                    ORIGINAL_HASH_AND_SIZE as `key`,
                    LISTAGG(ID, ',') WITHIN GROUP (ORDER BY ID) AS ids
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
                  GROUP BY ORIGINAL_HASH_AND_SIZE
                  ORDER BY ORIGINAL_HASH_AND_SIZE
            """)
    @UseRowMapper(H2DuplicateFileIdRowMapper.class)
    ResultIterable<GroupedIdCollection> fetchDuplicateIdsByHashOf(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT
                    PATH_AND_SIZE_AND_LAST_MODIFIED as key,
                    LISTAGG(ID, ',') WITHIN GROUP (ORDER BY ID) AS ids
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS IN (<changeStatuses>)
                    AND FILE_TYPE IN (<fileTypes>)
                  GROUP BY PATH_AND_SIZE_AND_LAST_MODIFIED
                  ORDER BY PATH_AND_SIZE_AND_LAST_MODIFIED
            """)
    @UseRowMapper(H2DuplicateFileIdRowMapper.class)
    ResultIterable<GroupedIdCollection> fetchDuplicateIdsByTimestampOf(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("changeStatuses") Set<Change> changeStatuses,
            @BindList("fileTypes") Set<FileType> fileTypes);

    @SqlQuery("""
                SELECT SUM(ORIGINAL_SIZE_BYTES)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    long getOriginalSizeBytes(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT SUM(ORIGINAL_SIZE_BYTES)
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND ID = :fileId
            """)
    boolean containsFileId(
            @BindMethods("id") FileMetadataSetId id,
            @Bind("fileId") UUID fileId);

    @SqlQuery("""
                SELECT
                    SET_ID AS setId,
                    ID AS id,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    FILE_TYPE AS fileType
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND STATUS <> 'DELETED'
            """)
    @UseRowMapper(H2FileMetadataIndexRowMapper.class)
    ResultIterable<FileMetadataIndex> findFileIndex(@BindMethods("id") FileMetadataSetId id);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND ERROR IS NOT NULL
            """)
    @UseRowMapper(H2FileMetadataRowMapper.class)
    List<FileMetadata> findErrorsOf(@BindMethods("id") FileMetadataSetId id);

    @SqlUpdate("""
                UPDATE FILE_METADATA
                  SET ARCHIVE_ID = :archiveMetadataId
                  WHERE SET_ID = :id.id
                    AND ID = :fileId
            """)
    void updateArchiveMetadataId(
            @BindMethods("id") FileMetadataSetId id,
            @Bind("fileId") UUID fileId,
            @Bind("archiveMetadataId") @Nullable UUID archiveMetadataId);

    @SqlUpdate("""
                INSERT INTO FILE_METADATA (
                    SET_ID,
                    ID,
                    FILE_SYSTEM_KEY,
                    ABSOLUTE_PATH,
                    ORIGINAL_HASH,
                    ORIGINAL_SIZE_BYTES,
                    ORIGINAL_HASH_AND_SIZE,
                    PATH_AND_SIZE_AND_LAST_MODIFIED,
                    LAST_MODIFIED,
                    LAST_ACCESSED,
                    CREATED,
                    POSIX_PERMISSIONS,
                    OWNER_NAME,
                    GROUP_NAME,
                    FILE_TYPE,
                    HIDDEN,
                    STATUS,
                    ARCHIVE_ID,
                    ERROR
                )
                (
                    SELECT
                        :result.id,
                        SRC.ID,
                        SRC.FILE_SYSTEM_KEY,
                        SRC.ABSOLUTE_PATH,
                        SRC.ORIGINAL_HASH,
                        SRC.ORIGINAL_SIZE_BYTES,
                        SRC.ORIGINAL_HASH_AND_SIZE,
                        SRC.PATH_AND_SIZE_AND_LAST_MODIFIED,
                        SRC.LAST_MODIFIED,
                        SRC.LAST_ACCESSED,
                        SRC.CREATED,
                        SRC.POSIX_PERMISSIONS,
                        SRC.OWNER_NAME,
                        SRC.GROUP_NAME,
                        SRC.FILE_TYPE,
                        SRC.HIDDEN,
                        SRC.STATUS,
                        SRC.ARCHIVE_ID,
                        SRC.ERROR
                      FROM FILE_METADATA SRC
                      WHERE SRC.SET_ID = :source.id
                        AND SRC.ABSOLUTE_PATH IN (
                            SELECT CONT.ABSOLUTE_PATH
                              FROM FILE_METADATA CONT
                              WHERE CONT.SET_ID = :containedIn.id
                        )
                )
            """)
    void intersectByPath(
            @BindMethods("result") FileMetadataSetId resultSet,
            @BindMethods("source") FileMetadataSetId lastIncrement,
            @BindMethods("containedIn") FileMetadataSetId restoreScope);

    @SqlUpdate("""
                INSERT INTO FILE_METADATA (
                    SET_ID,
                    ID,
                    FILE_SYSTEM_KEY,
                    ABSOLUTE_PATH,
                    ORIGINAL_HASH,
                    ORIGINAL_SIZE_BYTES,
                    ORIGINAL_HASH_AND_SIZE,
                    PATH_AND_SIZE_AND_LAST_MODIFIED,
                    LAST_MODIFIED,
                    LAST_ACCESSED,
                    CREATED,
                    POSIX_PERMISSIONS,
                    OWNER_NAME,
                    GROUP_NAME,
                    FILE_TYPE,
                    HIDDEN,
                    STATUS,
                    ARCHIVE_ID,
                    ERROR
                )
                (
                    SELECT
                        :result.id,
                        SRC.ID,
                        SRC.FILE_SYSTEM_KEY,
                        SRC.ABSOLUTE_PATH,
                        SRC.ORIGINAL_HASH,
                        SRC.ORIGINAL_SIZE_BYTES,
                        SRC.ORIGINAL_HASH_AND_SIZE,
                        SRC.PATH_AND_SIZE_AND_LAST_MODIFIED,
                        SRC.LAST_MODIFIED,
                        SRC.LAST_ACCESSED,
                        SRC.CREATED,
                        SRC.POSIX_PERMISSIONS,
                        SRC.OWNER_NAME,
                        SRC.GROUP_NAME,
                        SRC.FILE_TYPE,
                        SRC.HIDDEN,
                        SRC.STATUS,
                        SRC.ARCHIVE_ID,
                        SRC.ERROR
                      FROM FILE_METADATA SRC
                      WHERE SRC.SET_ID = :source.id
                        AND SRC.FILE_TYPE IN (<contentSources>)
                        AND SRC.ABSOLUTE_PATH IN (
                            SELECT CONT.ABSOLUTE_PATH
                              FROM CHANGE_STATUS CONT
                              WHERE CONT.SET_ID = :containedIn.id
                                AND CONT.STATUS IN (<restoreContent>)
                        )
                )
            """)
    void keepChangedContent(
            @BindMethods("result") FileMetadataSetId resultSet,
            @BindMethods("source") FileMetadataSetId lastIncrement,
            @BindMethods("containedIn") BackupPathChangeStatusMapId changeStats,
            @BindList("contentSources") Set<FileType> fileTypes,
            @BindList("restoreContent") Set<Change> changes);

    @SqlUpdate("""
                INSERT INTO FILE_METADATA (
                    SET_ID,
                    ID,
                    FILE_SYSTEM_KEY,
                    ABSOLUTE_PATH,
                    ORIGINAL_HASH,
                    ORIGINAL_SIZE_BYTES,
                    ORIGINAL_HASH_AND_SIZE,
                    PATH_AND_SIZE_AND_LAST_MODIFIED,
                    LAST_MODIFIED,
                    LAST_ACCESSED,
                    CREATED,
                    POSIX_PERMISSIONS,
                    OWNER_NAME,
                    GROUP_NAME,
                    FILE_TYPE,
                    HIDDEN,
                    STATUS,
                    ARCHIVE_ID,
                    ERROR
                )
                (
                    SELECT
                        :result.id,
                        SRC.ID,
                        SRC.FILE_SYSTEM_KEY,
                        SRC.ABSOLUTE_PATH,
                        SRC.ORIGINAL_HASH,
                        SRC.ORIGINAL_SIZE_BYTES,
                        SRC.ORIGINAL_HASH_AND_SIZE,
                        SRC.PATH_AND_SIZE_AND_LAST_MODIFIED,
                        SRC.LAST_MODIFIED,
                        SRC.LAST_ACCESSED,
                        SRC.CREATED,
                        SRC.POSIX_PERMISSIONS,
                        SRC.OWNER_NAME,
                        SRC.GROUP_NAME,
                        SRC.FILE_TYPE,
                        SRC.HIDDEN,
                        SRC.STATUS,
                        SRC.ARCHIVE_ID,
                        SRC.ERROR
                      FROM FILE_METADATA SRC
                      WHERE SRC.SET_ID = :source.id
                        AND SRC.FILE_TYPE IN (<fileTypes>)
                        AND SRC.ABSOLUTE_PATH IN (
                            SELECT CONT.ABSOLUTE_PATH
                              FROM CHANGE_STATUS CONT
                              WHERE CONT.SET_ID = :containedIn.id
                        )
                )
            """)
    void keepChangedMetadata(
            @BindMethods("result") FileMetadataSetId resultSet,
            @BindMethods("source") FileMetadataSetId fromSet,
            @BindList("fileTypes") Set<FileType> fileTypes,
            @BindMethods("containedIn") BackupPathChangeStatusMapId changeStats);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id
                    AND ID = :file
            """)
    @UseRowMapper(H2FileMetadataRowMapper.class)
    Optional<FileMetadata> findFileById(
            @Bind("id") UUID id,
            @Bind("file") UUID file);

    @SqlQuery("""
                SELECT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND ID IN (<files>)
                  ORDER BY ABSOLUTE_PATH ASC
            """)
    @UseRowMapper(H2FileMetadataRowMapper.class)
    List<FileMetadata> findFilesByIds(
            @BindMethods("id") FileMetadataSetId id,
            @BindList("files") Collection<UUID> files);

    @SqlQuery("""
                SELECT COUNT(*) > 0
                  FROM FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND ABSOLUTE_PATH = :absolutePath.uri
            """)
    boolean containsPath(
            @BindMethods("id") FileMetadataSetId id,
            @BindBean("absolutePath") BackupPath absolutePath);

    @SqlUpdate("""
                INSERT INTO FILE_METADATA (
                    SET_ID,
                    ID,
                    FILE_SYSTEM_KEY,
                    ABSOLUTE_PATH,
                    ORIGINAL_HASH,
                    ORIGINAL_SIZE_BYTES,
                    ORIGINAL_HASH_AND_SIZE,
                    PATH_AND_SIZE_AND_LAST_MODIFIED,
                    LAST_MODIFIED,
                    LAST_ACCESSED,
                    CREATED,
                    POSIX_PERMISSIONS,
                    OWNER_NAME,
                    GROUP_NAME,
                    FILE_TYPE,
                    HIDDEN,
                    STATUS,
                    ARCHIVE_ID,
                    ERROR
                )
                (
                    SELECT
                        :result.id,
                        SRC.ID,
                        SRC.FILE_SYSTEM_KEY,
                        SRC.ABSOLUTE_PATH,
                        SRC.ORIGINAL_HASH,
                        SRC.ORIGINAL_SIZE_BYTES,
                        SRC.ORIGINAL_HASH_AND_SIZE,
                        SRC.PATH_AND_SIZE_AND_LAST_MODIFIED,
                        SRC.LAST_MODIFIED,
                        SRC.LAST_ACCESSED,
                        SRC.CREATED,
                        SRC.POSIX_PERMISSIONS,
                        SRC.OWNER_NAME,
                        SRC.GROUP_NAME,
                        SRC.FILE_TYPE,
                        SRC.HIDDEN,
                        SRC.STATUS,
                        SRC.ARCHIVE_ID,
                        SRC.ERROR
                      FROM FILE_METADATA SRC
                      WHERE SRC.SET_ID = :source.id
                        AND SRC.STATUS <> 'DELETED'
                )
            """)
    void copyAllNotDeleted(
            @BindMethods("result") FileMetadataSetId resultSet,
            @BindMethods("source") FileMetadataSetId fromSet);

    @SqlUpdate("""
                INSERT INTO FILE_METADATA (
                    SET_ID,
                    ID,
                    FILE_SYSTEM_KEY,
                    ABSOLUTE_PATH,
                    ORIGINAL_HASH,
                    ORIGINAL_SIZE_BYTES,
                    ORIGINAL_HASH_AND_SIZE,
                    PATH_AND_SIZE_AND_LAST_MODIFIED,
                    LAST_MODIFIED,
                    LAST_ACCESSED,
                    CREATED,
                    POSIX_PERMISSIONS,
                    OWNER_NAME,
                    GROUP_NAME,
                    FILE_TYPE,
                    HIDDEN,
                    STATUS,
                    ARCHIVE_ID,
                    ERROR
                )
                (
                    SELECT
                        :result.id,
                        SRC.ID,
                        SRC.FILE_SYSTEM_KEY,
                        SRC.ABSOLUTE_PATH,
                        SRC.ORIGINAL_HASH,
                        SRC.ORIGINAL_SIZE_BYTES,
                        SRC.ORIGINAL_HASH_AND_SIZE,
                        SRC.PATH_AND_SIZE_AND_LAST_MODIFIED,
                        SRC.LAST_MODIFIED,
                        SRC.LAST_ACCESSED,
                        SRC.CREATED,
                        SRC.POSIX_PERMISSIONS,
                        SRC.OWNER_NAME,
                        SRC.GROUP_NAME,
                        SRC.FILE_TYPE,
                        SRC.HIDDEN,
                        SRC.STATUS,
                        SRC.ARCHIVE_ID,
                        SRC.ERROR
                      FROM FILE_METADATA SRC
                      WHERE SRC.SET_ID = :source.id
                )
            """)
    void copyAll(
            @BindMethods("result") FileMetadataSetId resultSet,
            @BindMethods("source") FileMetadataSetId fromSet);

    @SqlQuery("""
                SELECT DISTINCT
                    ID AS id,
                    FILE_SYSTEM_KEY AS fileSystemKey,
                    ABSOLUTE_PATH AS absolutePath,
                    ORIGINAL_HASH AS originalHash,
                    ORIGINAL_SIZE_BYTES AS originalSizeBytes,
                    LAST_MODIFIED AS lastModifiedUtcEpochSeconds,
                    LAST_ACCESSED AS lastAccessedUtcEpochSeconds,
                    CREATED AS createdUtcEpochSeconds,
                    POSIX_PERMISSIONS AS posixPermissions,
                    OWNER_NAME AS owner,
                    GROUP_NAME AS `group`,
                    FILE_TYPE AS fileType,
                    HIDDEN AS hidden,
                    STATUS AS status,
                    ARCHIVE_ID AS archiveMetadataId,
                    ERROR AS error
                  FROM FILE_METADATA
                  WHERE SET_ID = :fileId.id
                    AND ID IN (
                      SELECT F.FILE
                        FROM ARCHIVED_FILE_METADATA A
                          JOIN ARCHIVE_FILE_METADATA_FILES F
                            ON F.SET_ID = A.SET_ID
                              AND F.ID = A.ID
                              AND F.VERSION = A.BACKUP_INCREMENT
                        WHERE A.SET_ID = :id.id
                          AND A.BACKUP_INCREMENT = :locator.backupIncrement
                          AND A.ARCHIVE = :locator.entryName
                    )
            """)
    @UseRowMapper(H2FileMetadataRowMapper.class)
    SortedSet<FileMetadata> findFileMetadataByArchiveLocator(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @BindMethods("fileId") FileMetadataSetId files,
            @BindBean("locator") ArchiveEntryLocator currentLocator);
}
