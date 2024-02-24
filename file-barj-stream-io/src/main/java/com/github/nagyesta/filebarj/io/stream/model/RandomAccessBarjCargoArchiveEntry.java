package com.github.nagyesta.filebarj.io.stream.model;

import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.FixedRangeInputStream;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Represents an entry in a BaRJ cargo archive.
 */
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
public class RandomAccessBarjCargoArchiveEntry implements BarjCargoArchiveEntry {

    @NonNull
    private final BarjCargoArchiveFileInputStreamSource source;
    @NonNull
    private final BarjCargoEntityIndex entityIndex;

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
        return source.getStreamFor(entityIndex.getContent(), key);
    }

    @Override
    @NotNull
    public String getLinkTarget(@Nullable final SecretKey key) throws IOException {
        if (getFileType() != FileType.SYMBOLIC_LINK) {
            throw new IllegalArgumentException("Must be called with a symbolic link!");
        }
        try (var stream = source.getStreamFor(entityIndex.getContent(), key)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    @Nullable
    public String getMetadata(@Nullable final SecretKey key) throws IOException {
        if (entityIndex.getMetadata().getOriginalSizeBytes() == 0) {
            return null;
        }
        try (var stream = source.getStreamFor(entityIndex.getMetadata(), key)) {
            return new String(stream.readAllBytes());
        }
    }

    @Override
    @NotNull
    public InputStream getRawContentAndMetadata() throws IOException {
        final var start = entityIndex.getContentOrElseMetadata().getAbsoluteStartIndexInclusive();
        final var length = entityIndex.getMetadata().getAbsoluteEndIndexExclusive() - start;
        return new FixedRangeInputStream(source.openStreamForSequentialAccess(), start, length);
    }

    @Override
    public void skipContent() throws IOException {
        //noop
    }

    @Override
    public void skipMetadata() throws IOException {
        //noop
    }
}
