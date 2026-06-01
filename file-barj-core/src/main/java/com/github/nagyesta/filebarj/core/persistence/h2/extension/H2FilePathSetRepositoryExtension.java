package com.github.nagyesta.filebarj.core.persistence.h2.extension;

import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.List;
import java.util.Set;

@Transaction(value = TransactionIsolationLevel.READ_COMMITTED)
public interface H2FilePathSetRepositoryExtension
        extends H2BaseRepositoryExtension<FilePathSetId, String, String> {

    @SqlUpdate("""
                DELETE FROM FILE_PATHS
                  WHERE SET_ID = :id.id
            """)
    @Override
    void deleteAll(@BindMethods("id") FilePathSetId id);

    @SqlUpdate("""
                DELETE FROM FILE_PATHS
                  WHERE SET_ID = :id.id
                    AND PATH = :path
            """)
    @Override
    void deleteOne(
            @BindMethods("id") FilePathSetId id,
            @Bind("path") String path);

    @SqlQuery("""
                SELECT COUNT(*) > 0
                  FROM FILE_PATHS
                  WHERE SET_ID = :id.id
            """)
    @Override
    boolean exists(@BindMethods("id") FilePathSetId id);

    @SqlQuery("""
                SELECT PATH
                  FROM FILE_PATHS
                  WHERE SET_ID = :id.id
                  ORDER BY PATH ASC
                  LIMIT :limit
                  OFFSET :offset
            """)
    @Override
    List<String> fetchPage(
            @BindMethods("id") FilePathSetId id,
            @Bind("limit") long limit,
            @Bind("offset") long offset);

    @SqlQuery("""
                SELECT PATH
                  FROM FILE_PATHS
                  WHERE SET_ID = :id.id
                  ORDER BY PATH DESC
                  LIMIT :limit
                  OFFSET :offset
            """)
    @Override
    List<String> fetchPageReverse(
            @BindMethods("id") FilePathSetId id,
            @Bind("limit") long limit,
            @Bind("offset") long offset);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM FILE_PATHS
                  WHERE SET_ID = :id.id
            """)
    @Override
    long countAll(@BindMethods("id") FilePathSetId id);

    @SqlQuery("""
                SELECT P.PATH
                  FROM FILE_PATHS P
                  WHERE P.SET_ID = :id.id
                    AND P.PATH IN (<paths>)
            """)
    Set<String> exists(
            @BindMethods("id") FilePathSetId id,
            @BindList("paths") Set<String> paths);

    @SqlUpdate("""
                INSERT INTO FILE_PATHS (SET_ID, PATH, LOWER_PATH)
                  VALUES (:id.id, :path, LOWER(:path))
            """)
    @Override
    void appendTo(
            @BindMethods("id") FilePathSetId id,
            @Bind("path") String path);

    @SqlUpdate("""
                INSERT INTO FILE_PATHS (SET_ID, PATH, LOWER_PATH)
                (
                    SELECT CAST(:result.id AS uuid) AS SET_ID, SRC.PATH AS PATH, SRC.LOWER_PATH AS LOWER_PATH
                      FROM FILE_PATHS SRC
                      WHERE SRC.SET_ID = :source.id
                    EXCEPT
                    SELECT CAST(:result.id AS uuid) AS SET_ID, REM.PATH AS PATH, REM.LOWER_PATH AS LOWER_PATH
                      FROM FILE_PATHS REM
                      WHERE REM.SET_ID = :remove.id
                )
            """)
    void subtract(
            @BindMethods("result") FilePathSetId resultSet,
            @BindMethods("source") FilePathSetId fromSet,
            @BindMethods("remove") FilePathSetId removeSet);

    @SqlQuery("""
                SELECT A.PATH
                  FROM FILE_PATHS A
                    JOIN FILE_PATHS B
                      ON B.SET_ID = A.SET_ID
                      AND B.LOWER_PATH = A.LOWER_PATH
                      AND B.PATH <> A.PATH
                  WHERE A.SET_ID = :id.id
            """)
    List<String> detectCaseInsensitivityIssues(@BindMethods("id") FilePathSetId id);

}
