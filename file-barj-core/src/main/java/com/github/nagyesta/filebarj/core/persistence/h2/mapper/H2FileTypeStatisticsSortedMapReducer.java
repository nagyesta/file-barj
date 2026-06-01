package com.github.nagyesta.filebarj.core.persistence.h2.mapper;

import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.jdbi.v3.core.result.RowReducer;
import org.jdbi.v3.core.result.RowView;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

public class H2FileTypeStatisticsSortedMapReducer
        implements RowReducer<SortedMap<FileType, Long>, Map.Entry<FileType, Long>> {

    @Override
    public SortedMap<FileType, Long> container() {
        return new TreeMap<>();
    }

    @Override
    public void accumulate(final SortedMap<FileType, Long> container, final RowView rowView) {
        final var fileType = Enum.valueOf(FileType.class, rowView.getColumn("fileType", String.class));
        final var number = rowView.getColumn("number", Long.class);
        container.put(fileType, number);
    }

    @Override
    public Stream<Map.Entry<FileType, Long>> stream(final SortedMap<FileType, Long> container) {
        return container.entrySet().stream();
    }
}
