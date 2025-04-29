package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Temporary file variant of the {@link BaseBarjCargoArchiverFileOutputStream}.
 */
public class TempBarjCargoArchiverFileOutputStream extends BaseBarjCargoArchiverFileOutputStream {
    /**
     * Creates a new instance and sets the parameters needed for the BaRJ cargo streaming archival
     * file operations.
     *
     * @param config   The configuration for the BaRJ cargo archive
     * @param fileName The name of the file
     * @throws IOException If we cannot create the folder or write to it.
     */
    public TempBarjCargoArchiverFileOutputStream(
            final @NotNull BarjCargoOutputStreamConfiguration config,
            final String fileName) throws IOException {
        super(BarjCargoOutputStreamConfiguration.builder()
                .folder(config.getFolder())
                .prefix(fileName)
                .compressionFunction(config.getCompressionFunction())
                .hashAlgorithm(config.getHashAlgorithm())
                .build());
    }

    /**
     * Returns an input stream for the content and the metadata part of the archived entity.
     *
     * @param content  defines the boundaries of the content part
     * @param metadata defines the boundaries of the metadata part
     * @return an input stream
     * @throws IOException If the input stream cannot be created
     */
    public InputStream getStream(
            final @NonNull BarjCargoEntryBoundaries content,
            final @NonNull BarjCargoEntryBoundaries metadata) throws IOException {
        final var start = content.getAbsoluteStartIndexInclusive();
        final var length = metadata.getAbsoluteEndIndexExclusive() - start;
        return new FixedRangeInputStream(new MergingFileInputStream(getDataFilesWritten()), start, length);
    }

    /**
     * Deletes all the temp files.
     *
     * @throws IOException If the files cannot be deleted
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void delete() throws IOException {
        getDataFilesWritten().stream()
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
