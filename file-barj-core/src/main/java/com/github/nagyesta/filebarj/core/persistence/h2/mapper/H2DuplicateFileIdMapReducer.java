package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import org.jdbi.v3.core.result.RowReducer;
import org.jdbi.v3.core.result.RowView;

import java.util.*;
import java.util.stream.Stream;

public class H2DuplicateFileIdMapReducer
        implements RowReducer<Map<String, List<UUID>>, Map.Entry<String, List<UUID>>> {

    @Override
    public Map<String, List<UUID>> container() {
        return new HashMap<>();
    }

    @Override
    public void accumulate(final Map<String, List<UUID>> container, final RowView rowView) {
        final var status = rowView.getColumn("key", String.class);
        final var ids = Arrays.stream(rowView.getColumn("ids", String.class).split(","))
                .map(UUID::fromString)
                .toList();
        container.put(status, ids);
    }

    @Override
    public Stream<Map.Entry<String, List<UUID>>> stream(final Map<String, List<UUID>> container) {
        return container.entrySet().stream();
    }
}
