package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.BackupChange;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class H2BackupChangeRowMapper
        implements RowMapper<BackupChange> {

    @Override
    public BackupChange map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        return new BackupChange(
                BackupPath.fromUri(rs.getString("backupPath")),
                Enum.valueOf(Change.class, rs.getString("status"))
        );
    }
}
