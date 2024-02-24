package com.github.nagyesta.filebarj.io.stream.model;

import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveEntryIterator;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.FixedRangeInputStream;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Represents an entry in a BaRJ cargo archive.
 */
@EqualsAndHashCode
@ToString
public class SequentialBarjCargoArchiveEntry implements BarjCargoArchiveEntry {

    @NonNull
    private final BarjCargoArchiveFileInputStreamSource source;
    private final BarjCargoArchiveEntryIterator iterator;
    @Getter
    @NonNull
    private final BarjCargoEntityIndex entityIndex;

    /**
     * Creates an instance and prepares it for iteration.
     *
     * @param source      The source representing the archive
     * @param iterator    The iterator
     * @param entityIndex The index describing the entry's location in the archive
     */
    public SequentialBarjCargoArchiveEntry(@NonNull final BarjCargoArchiveFileInputStreamSource source,
                                           @NonNull final BarjCargoArchiveEntryIterator iterator,
                                           @NonNull final BarjCargoEntityIndex entityIndex) {
        this.source = source;
        this.iterator = iterator;
        this.entityIndex = entityIndex;
    }

    @Override
    public String getPath() {
        return entityIndex.getPath();
    }

    @Override
    public FileType getFileType() {
        return entityIndex.getFileType();
    }

    @Override
    @NotNull
    public InputStream getFileContent(@Nullable final SecretKey key) throws IOException {
        if (getFileType() != FileType.REGULAR_FILE) {
            throw new IllegalArgumentException("Must be called with a regular file!");
        }
        return source.getNextStreamFor(iterator.getStream(), entityIndex.getContent(), key);
    }

    @Override
    @NotNull
    public String getLinkTarget(@Nullable final SecretKey key) throws IOException {
        if (getFileType() != FileType.SYMBOLIC_LINK) {
            throw new IllegalArgumentException("Must be called with a symbolic link!");
        }
        try (var stream = source.getNextStreamFor(iterator.getStream(), entityIndex.getContent(), key)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    @Nullable
    public String getMetadata(@Nullable final SecretKey key) throws IOException {
        final var metadata = entityIndex.getMetadata();
        if (metadata.getArchivedSizeBytes() == 0) {
            return null;
        }
        try (var stream = source.getNextStreamFor(iterator.getStream(), metadata, key)) {
            final var allBytes = stream.readAllBytes();
            if (metadata.getOriginalSizeBytes() == 0) {
                return null;
            }
            return new String(allBytes);
        }
    }

    @Override
    @NotNull
    public InputStream getRawContentAndMetadata() throws IOException {
        final var start = entityIndex.getContentOrElseMetadata().getAbsoluteStartIndexInclusive();
        final var length = entityIndex.getMetadata().getAbsoluteEndIndexExclusive() - start;
        return CloseShieldInputStream.wrap(new FixedRangeInputStream(iterator.getStream(), 0, length));
    }

    @Override
    public void skipContent() throws IOException {
        if (getFileType() == FileType.DIRECTORY) {
            return;
        }
        iterator.getStream().skipNBytes(entityIndex.getContent().getArchivedSizeBytes());
    }

    @Override
    public void skipMetadata() throws IOException {
        if (entityIndex.getMetadata().getArchivedSizeBytes() == 0) {
            return;
        }
        iterator.getStream().skipNBytes(entityIndex.getMetadata().getArchivedSizeBytes());
    }
}
