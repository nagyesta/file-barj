package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class H2ArchivedFileMetadataRowMapper
        implements RowMapper<ArchivedFileMetadata> {

    @Override
    public ArchivedFileMetadata map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        final var files = Optional.ofNullable(rs.getString("files"))
                .map(s -> Arrays.stream(s.split(","))
                        .map(UUID::fromString)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
        final var archiveLocation = ArchiveEntryLocator.builder()
                .backupIncrement(rs.getInt("backupIncrement"))
                .entryName(rs.getObject("archive", UUID.class))
                .build();
        return ArchivedFileMetadata.builder()
                .id(rs.getObject("id", UUID.class))
                .archiveLocation(archiveLocation)
                .originalHash(rs.getString("originalHash"))
                .archivedHash(rs.getString("archivedHash"))
                .files(files)
                .build();
    }
}
