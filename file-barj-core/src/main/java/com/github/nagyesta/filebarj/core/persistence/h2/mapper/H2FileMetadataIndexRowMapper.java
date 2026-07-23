package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.FileMetadataIndex;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class H2FileMetadataIndexRowMapper
        implements RowMapper<FileMetadataIndex> {

    @Override
    public FileMetadataIndex map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        return new FileMetadataIndex(
                rs.getObject("setId", UUID.class),
                rs.getObject("id", UUID.class),
                BackupPath.fromUri(rs.getString("absolutePath")),
                rs.getString("originalHash"),
                rs.getLong("originalSizeBytes"),
                rs.getLong("lastModifiedUtcEpochSeconds"),
                Enum.valueOf(FileType.class, rs.getString("fileType"))
        );
    }
}
