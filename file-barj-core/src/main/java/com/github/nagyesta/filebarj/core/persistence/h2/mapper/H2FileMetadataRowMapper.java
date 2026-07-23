package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class H2FileMetadataRowMapper implements RowMapper<FileMetadata> {

    @Override
    public FileMetadata map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        return FileMetadata.builder()
                .id(rs.getObject("id", UUID.class))
                .fileSystemKey(rs.getString("fileSystemKey"))
                .absolutePath(BackupPath.fromUri(rs.getString("absolutePath")))
                .originalHash(rs.getString("originalHash"))
                .originalSizeBytes(rs.getLong("originalSizeBytes"))
                .lastModifiedUtcEpochSeconds(rs.getLong("lastModifiedUtcEpochSeconds"))
                .lastAccessedUtcEpochSeconds(rs.getLong("lastAccessedUtcEpochSeconds"))
                .createdUtcEpochSeconds(rs.getLong("createdUtcEpochSeconds"))
                .posixPermissions(rs.getString("posixPermissions"))
                .owner(rs.getString("owner"))
                .group(rs.getString("group"))
                .fileType(Enum.valueOf(FileType.class, rs.getString("fileType")))
                .hidden(rs.getBoolean("hidden"))
                .status(Enum.valueOf(Change.class, rs.getString("status")))
                .archiveMetadataId(rs.getObject("archiveMetadataId", UUID.class))
                .error(rs.getString("error"))
                .build();
    }
}
