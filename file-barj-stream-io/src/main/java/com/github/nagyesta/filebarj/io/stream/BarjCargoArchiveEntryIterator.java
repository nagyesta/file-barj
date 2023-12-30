package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import com.github.nagyesta.filebarj.io.stream.model.SequentialBarjCargoArchiveEntry;
import lombok.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * Iterator for reading all SequentialBarjCargoArchiveEntry items from a BaRJ cargo archive in order.
 */
public class BarjCargoArchiveEntryIterator implements Iterator<SequentialBarjCargoArchiveEntry>, Closeable {

    private final BarjCargoArchiveFileInputStreamSource source;
    private final InputStream inputStream;
    private final Iterator<BarjCargoEntityIndex> iterator;

    /**
     * Creates an instance and prepares it for iteration.
     *
     * @param source the source representing the archive
     * @param list   the list of entries we need to iterate through in order
     * @throws IOException when the list cannot be read
     */
    public BarjCargoArchiveEntryIterator(
            @NonNull final BarjCargoArchiveFileInputStreamSource source,
            @NonNull final List<BarjCargoEntityIndex> list) throws IOException {
        this.source = source;
        this.inputStream = source.openStreamForSequentialAccess();
        this.iterator = list.listIterator();
    }

    /**
     * Creates an instance and prepares it for iteration on the relevant files.
     *
     * @param source        the source representing the archive
     * @param relevantFiles The list of relevant files
     * @param list          the list of entries we need to iterate through in order
     * @throws IOException when the list cannot be read
     */
    public BarjCargoArchiveEntryIterator(
            @NonNull final BarjCargoArchiveFileInputStreamSource source,
            @NonNull final List<Path> relevantFiles,
            @NonNull final List<BarjCargoEntityIndex> list) throws IOException {
        this.source = source;
        this.inputStream = source.openStreamForSequentialAccess(relevantFiles, list);
        this.iterator = list.listIterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    /**
     * Returns the underlying stream where the items are read from.
     *
     * @return the stream
     */
    public InputStream getStream() {
        return inputStream;
    }

    @Override
    public SequentialBarjCargoArchiveEntry next() {
        return new SequentialBarjCargoArchiveEntry(source, this, iterator.next());
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
