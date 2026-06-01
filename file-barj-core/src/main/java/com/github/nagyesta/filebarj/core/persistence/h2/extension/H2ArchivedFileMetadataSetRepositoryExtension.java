package com.github.nagyesta.filebarj.core.persistence.h2.extension;

import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.mapper.H2ArchivedFileMetadataRowMapper;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.*;

@Transaction(value = TransactionIsolationLevel.READ_COMMITTED)
public interface H2ArchivedFileMetadataSetRepositoryExtension
        extends H2BaseRepositoryExtension<ArchivedFileMetadataSetId, UUID, ArchivedFileMetadata> {

    @SqlUpdate("""
                DELETE FROM ARCHIVED_FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    void deleteAll(@BindMethods("id") ArchivedFileMetadataSetId id);

    @SqlUpdate("""
                DELETE FROM ARCHIVED_FILE_METADATA
                  WHERE SET_ID = :id.id
                    AND ID = :fileId
            """)
    @Override
    void deleteOne(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @Bind("fileId") UUID fileId);

    @SqlQuery("""
                SELECT COUNT(*) > 0
                  FROM ARCHIVED_FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    boolean exists(@BindMethods("id") ArchivedFileMetadataSetId id);

    @SqlQuery("""
                SELECT
                    A.ID AS id,
                    A.BACKUP_INCREMENT AS backupIncrement,
                    A.ARCHIVE AS archive,
                    A.ORIGINAL_HASH AS originalHash,
                    A.ARCHIVED_HASH AS archivedHash,
                    LISTAGG(F.FILE, ',') WITHIN GROUP (ORDER BY F.FILE) AS files
                  FROM ARCHIVED_FILE_METADATA A
                    LEFT JOIN ARCHIVE_FILE_METADATA_FILES F
                      ON F.SET_ID = A.SET_ID
                        AND F.ID = A.ID
                        AND F.VERSION = A.BACKUP_INCREMENT
                  WHERE A.SET_ID = :id.id
                  GROUP BY A.ID, A.BACKUP_INCREMENT, A.ARCHIVE, A.ORIGINAL_HASH, A.ARCHIVED_HASH
                  ORDER BY A.ID ASC
                  LIMIT :limit
                  OFFSET :offset
            """)
    @Override
    @UseRowMapper(H2ArchivedFileMetadataRowMapper.class)
    List<ArchivedFileMetadata> fetchPage(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @Bind("limit") long limit,
            @Bind("offset") long offset);

    @SqlQuery("""
                SELECT
                    A.ID AS id,
                    A.BACKUP_INCREMENT AS backupIncrement,
                    A.ARCHIVE AS archive,
                    A.ORIGINAL_HASH AS originalHash,
                    A.ARCHIVED_HASH AS archivedHash,
                    LISTAGG(F.FILE, ',') WITHIN GROUP (ORDER BY F.FILE) AS files
                  FROM ARCHIVED_FILE_METADATA A
                    LEFT JOIN ARCHIVE_FILE_METADATA_FILES F
                      ON F.SET_ID = A.SET_ID
                        AND F.ID = A.ID
                        AND F.VERSION = A.BACKUP_INCREMENT
                  WHERE A.SET_ID = :id.id
                  GROUP BY A.ID, A.BACKUP_INCREMENT, A.ARCHIVE, A.ORIGINAL_HASH, A.ARCHIVED_HASH
                  ORDER BY A.ID DESC
                  LIMIT :limit
                  OFFSET :offset
            """)
    @Override
    @UseRowMapper(H2ArchivedFileMetadataRowMapper.class)
    List<ArchivedFileMetadata> fetchPageReverse(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @Bind("limit") long limit,
            @Bind("offset") long offset);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM ARCHIVED_FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    @Override
    long countAll(@BindMethods("id") ArchivedFileMetadataSetId id);

    @SqlUpdate("""
                INSERT INTO ARCHIVED_FILE_METADATA (
                    SET_ID,
                    ID,
                    BACKUP_INCREMENT,
                    ARCHIVE,
                    ORIGINAL_HASH,
                    ARCHIVED_HASH
                  ) VALUES (
                    :id.id,
                    :entity.id,
                    :entity.archiveLocation.backupIncrement,
                    :entity.archiveLocation.entryName,
                    :entity.originalHash,
                    :entity.archivedHash
                  )
            """)
    @Override
    void appendTo(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @BindBean("entity") ArchivedFileMetadata entity);

    @SqlUpdate("""
                INSERT INTO ARCHIVE_FILE_METADATA_FILES (
                    SET_ID,
                    ID,
                    VERSION,
                    FILE
                  ) VALUES (
                    :id.id,
                    :entity.id,
                    :entity.archiveLocation.backupIncrement,
                    :file
                  )
            """)
    void appendFileTo(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @BindBean("entity") ArchivedFileMetadata entity,
            @Bind("file") UUID file);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM ARCHIVE_FILE_METADATA_FILES
                  WHERE SET_ID = :id.id
            """)
    long countAllFiles(@BindMethods("id") ArchivedFileMetadataSetId id);

    @SqlQuery("""
                SELECT FILE
                  FROM ARCHIVE_FILE_METADATA_FILES
                  WHERE SET_ID = :id.id
                    AND FILE IN (<fileIds>)
            """)
    Set<UUID> containsFileMetadataIds(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @BindList("fileIds") Collection<UUID> fileMetadataIds);

    @SqlQuery("""
                SELECT
                    A.ID AS id,
                    A.BACKUP_INCREMENT AS backupIncrement,
                    A.ARCHIVE AS archive,
                    A.ORIGINAL_HASH AS originalHash,
                    A.ARCHIVED_HASH AS archivedHash,
                    LISTAGG(F.FILE, ',') WITHIN GROUP (ORDER BY F.FILE) AS files
                  FROM ARCHIVE_FILE_METADATA_FILES F
                    JOIN ARCHIVED_FILE_METADATA A
                      ON A.SET_ID = F.SET_ID
                        AND A.ID = F.ID
                        AND A.BACKUP_INCREMENT = F.VERSION
                  WHERE F.SET_ID = :id.id
                    AND F.FILE = :fileId
                  GROUP BY A.ID, A.BACKUP_INCREMENT, A.ARCHIVE, A.ORIGINAL_HASH, A.ARCHIVED_HASH
                  ORDER BY A.ID ASC
            """)
    @UseRowMapper(H2ArchivedFileMetadataRowMapper.class)
    Optional<ArchivedFileMetadata> findByFileMetadataId(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @Bind("fileId") UUID fileMetadataId);

    @SqlQuery("""
                SELECT
                    A.ID AS id,
                    A.BACKUP_INCREMENT AS backupIncrement,
                    A.ARCHIVE AS archive,
                    A.ORIGINAL_HASH AS originalHash,
                    A.ARCHIVED_HASH AS archivedHash,
                    LISTAGG(F.FILE, ',') WITHIN GROUP (ORDER BY F.FILE) AS files
                  FROM ARCHIVE_FILE_METADATA_FILES F
                    JOIN ARCHIVED_FILE_METADATA A
                      ON A.SET_ID = F.SET_ID
                        AND A.ID = F.ID
                        AND A.BACKUP_INCREMENT = F.VERSION
                  WHERE F.SET_ID = :id.id
                    AND F.FILE IN (<fileIds>)
                  GROUP BY A.ID, A.BACKUP_INCREMENT, A.ARCHIVE, A.ORIGINAL_HASH, A.ARCHIVED_HASH
                  ORDER BY A.ID ASC
            """)
    @UseRowMapper(H2ArchivedFileMetadataRowMapper.class)
    List<ArchivedFileMetadata> findByFileMetadataIds(
            @BindMethods("id") ArchivedFileMetadataSetId id,
            @BindList("fileIds") Collection<UUID> fileMetadataIds);

    @SqlUpdate("""
                INSERT INTO ARCHIVED_FILE_METADATA (
                    SET_ID,
                    ID,
                    BACKUP_INCREMENT,
                    ARCHIVE,
                    ORIGINAL_HASH,
                    ARCHIVED_HASH
                  )
                  (
                      SELECT
                          :result.id,
                          SRC.ID,
                          SRC.BACKUP_INCREMENT,
                          SRC.ARCHIVE,
                          SRC.ORIGINAL_HASH,
                          SRC.ARCHIVED_HASH
                        FROM ARCHIVED_FILE_METADATA SRC
                        WHERE SRC.SET_ID = :source.id
                          AND SRC.ID IN (
                            SELECT F.ID
                              FROM ARCHIVE_FILE_METADATA_FILES F
                              WHERE F.SET_ID = :source.id
                                AND F.FILE IN (
                                  SELECT FM.ID
                                    FROM FILE_METADATA FM
                                    WHERE FM.SET_ID = :files.id
                                )
                          )
                  )
            """)
    void intersectWithFileMetadata(
            @BindMethods("result") ArchivedFileMetadataSetId result,
            @BindMethods("source") ArchivedFileMetadataSetId source,
            @BindMethods("files") FileMetadataSetId files);

    @SqlUpdate("""
                INSERT INTO ARCHIVE_FILE_METADATA_FILES (
                    SET_ID,
                    ID,
                    VERSION,
                    FILE
                  )
                  (
                      SELECT
                          :result.id,
                          SRC.ID,
                          SRC.VERSION,
                          SRC.FILE
                        FROM ARCHIVE_FILE_METADATA_FILES SRC
                        WHERE SRC.SET_ID = :source.id
                          AND SRC.FILE IN (
                              SELECT FM.ID
                                FROM FILE_METADATA FM
                                WHERE FM.SET_ID = :files.id
                          )
                  )
            """)
    void copyMatchingFileReferences(
            @BindMethods("result") ArchivedFileMetadataSetId result,
            @BindMethods("source") ArchivedFileMetadataSetId source,
            @BindMethods("files") FileMetadataSetId files);

    @SqlUpdate("""
                INSERT INTO ARCHIVED_FILE_METADATA (
                    SET_ID,
                    ID,
                    BACKUP_INCREMENT,
                    ARCHIVE,
                    ORIGINAL_HASH,
                    ARCHIVED_HASH
                  )
                  (
                      SELECT
                          :result.id,
                          SRC.ID,
                          SRC.BACKUP_INCREMENT,
                          SRC.ARCHIVE,
                          SRC.ORIGINAL_HASH,
                          SRC.ARCHIVED_HASH
                        FROM ARCHIVED_FILE_METADATA SRC
                        WHERE SRC.SET_ID = :source.id
                          AND SRC.BACKUP_INCREMENT IN (<increments>)
                  )
            """)
    void filterByBackupIncrements(
            @BindMethods("result") ArchivedFileMetadataSetId result,
            @BindMethods("source") ArchivedFileMetadataSetId source,
            @BindList("increments") SortedSet<Integer> versions);

    @SqlUpdate("""
                INSERT INTO ARCHIVE_FILE_METADATA_FILES (
                    SET_ID,
                    ID,
                    VERSION,
                    FILE
                  )
                  (
                      SELECT
                          :result.id,
                          SRC.ID,
                          SRC.VERSION,
                          SRC.FILE
                        FROM ARCHIVE_FILE_METADATA_FILES SRC
                        WHERE SRC.SET_ID = :source.id
                          AND SRC.VERSION IN (<increments>)
                  )
            """)
    void copyFileReferencesOfVersions(
            @BindMethods("result") ArchivedFileMetadataSetId result,
            @BindMethods("source") ArchivedFileMetadataSetId source,
            @BindList("increments") SortedSet<Integer> versions);

    @SqlQuery("""
                SELECT DISTINCT CONCAT('/', BACKUP_INCREMENT, '/', ARCHIVE)
                  FROM ARCHIVED_FILE_METADATA
                  WHERE SET_ID = :id.id
            """)
    Set<String> asEntryPaths(@BindMethods("id") ArchivedFileMetadataSetId id);

    @SqlUpdate("""
                INSERT INTO ARCHIVED_FILE_METADATA (
                    SET_ID,
                    ID,
                    BACKUP_INCREMENT,
                    ARCHIVE,
                    ORIGINAL_HASH,
                    ARCHIVED_HASH
                  )
                  (
                      SELECT
                          :result.id,
                          SRC.ID,
                          SRC.BACKUP_INCREMENT,
                          SRC.ARCHIVE,
                          SRC.ORIGINAL_HASH,
                          SRC.ARCHIVED_HASH
                        FROM ARCHIVED_FILE_METADATA SRC
                        WHERE SRC.SET_ID = :source.id
                  )
            """)
    void copyAll(
            @BindMethods("result") ArchivedFileMetadataSetId result,
            @BindMethods("source") ArchivedFileMetadataSetId source);

    @SqlUpdate("""
                INSERT INTO ARCHIVE_FILE_METADATA_FILES (
                    SET_ID,
                    ID,
                    VERSION,
                    FILE
                  )
                  (
                      SELECT
                          :result.id,
                          SRC.ID,
                          SRC.VERSION,
                          SRC.FILE
                        FROM ARCHIVE_FILE_METADATA_FILES SRC
                        WHERE SRC.SET_ID = :source.id
                  )
            """)
    void copyAllFileReferences(
            @BindMethods("result") ArchivedFileMetadataSetId result,
            @BindMethods("source") ArchivedFileMetadataSetId source);

}
