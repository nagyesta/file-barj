package com.github.nagyesta.filebarj.core.persistence.h2.extension;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.BackupChange;
import com.github.nagyesta.filebarj.core.persistence.h2.mapper.H2BackupChangeRowMapper;
import com.github.nagyesta.filebarj.core.persistence.h2.mapper.H2ChangeStatisticsSortedMapReducer;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

@Transaction(value = TransactionIsolationLevel.READ_COMMITTED)
public interface H2BackupPathChangeStatusMapRepositoryExtension
        extends H2BaseRepositoryExtension<BackupPathChangeStatusMapId, BackupPath, BackupChange> {

    @SqlUpdate("""
                DELETE FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
            """)
    @Override
    void deleteAll(@BindMethods("id") BackupPathChangeStatusMapId id);

    @SqlUpdate("""
                DELETE FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
                    AND ABSOLUTE_PATH = :path.uri
            """)
    @Override
    void deleteOne(
            @BindMethods("id") BackupPathChangeStatusMapId id,
            @BindBean("path") BackupPath valueKey);

    @SqlQuery("""
                SELECT COUNT(*) > 0
                  FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
            """)
    @Override
    boolean exists(BackupPathChangeStatusMapId id);

    @SqlQuery("""
                SELECT
                    ABSOLUTE_PATH AS backupPath,
                    STATUS AS status
                  FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
                  ORDER BY ABSOLUTE_PATH ASC
                  LIMIT :limit
                  OFFSET :offset
            """)
    @Override
    @UseRowMapper(H2BackupChangeRowMapper.class)
    List<BackupChange> fetchPage(
            @BindMethods("id") BackupPathChangeStatusMapId id,
            @Bind("limit") long limit,
            @Bind("offset") long offset);

    @SqlQuery("""
                SELECT
                    ABSOLUTE_PATH AS backupPath,
                    STATUS AS status
                  FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
                  ORDER BY ABSOLUTE_PATH DESC
                  LIMIT :limit
                  OFFSET :offset
            """)
    @Override
    @UseRowMapper(H2BackupChangeRowMapper.class)
    List<BackupChange> fetchPageReverse(
            @BindMethods("id") BackupPathChangeStatusMapId id,
            @Bind("limit") long limit,
            @Bind("offset") long offset);

    @SqlQuery("""
                SELECT COUNT(*)
                  FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
            """)
    @Override
    long countAll(@BindMethods("id") BackupPathChangeStatusMapId id);

    @SqlUpdate("""
                INSERT INTO CHANGE_STATUS (
                    SET_ID,
                    ABSOLUTE_PATH,
                    STATUS
                  ) VALUES (
                    :id.id,
                    :entity.backupPath.getUri,
                    :entity.status
                  )
            """)
    @Override
    void appendTo(
            @BindMethods("id") BackupPathChangeStatusMapId id,
            @BindMethods("entity") BackupChange value);

    @SqlQuery("""
                SELECT STATUS AS status, COUNT(*) AS number
                  FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
                  GROUP BY STATUS
            """)
    @UseRowReducer(H2ChangeStatisticsSortedMapReducer.class)
    SortedMap<Change, Long> countsByStatus(@BindMethods("id") BackupPathChangeStatusMapId id);

    @SqlQuery("""
                SELECT STATUS AS status
                  FROM CHANGE_STATUS
                  WHERE SET_ID = :id.id
                    AND ABSOLUTE_PATH = :path.uri
            """)
    Optional<String> getStatus(
            @BindMethods("id") BackupPathChangeStatusMapId id,
            @BindBean("path") BackupPath path);


}

