package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.model.enums.Change;
import org.jdbi.v3.core.result.RowReducer;
import org.jdbi.v3.core.result.RowView;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

public class H2ChangeStatisticsSortedMapReducer
        implements RowReducer<SortedMap<Change, Long>, Map.Entry<Change, Long>> {

    @Override
    public SortedMap<Change, Long> container() {
        return new TreeMap<>();
    }

    @Override
    public void accumulate(final SortedMap<Change, Long> container, final RowView rowView) {
        final var status = Enum.valueOf(Change.class, rowView.getColumn("status", String.class));
        final var number = rowView.getColumn("number", Long.class);
        container.put(status, number);
    }

    @Override
    public Stream<Map.Entry<Change, Long>> stream(final SortedMap<Change, Long> container) {
        return container.entrySet().stream();
    }
}
