package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.persistence.h2.entity.GroupedIdCollection;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

public class H2DuplicateFileIdRowMapper
        implements RowMapper<GroupedIdCollection> {

    @Override
    public GroupedIdCollection map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        final var key = rs.getString("key");
        final var ids = Arrays.stream(rs.getString("ids").split(","))
                .map(UUID::fromString)
                .toList();
        return new GroupedIdCollection(key, ids);
    }
}
