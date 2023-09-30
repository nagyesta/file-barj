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
            @NotNull final BarjCargoOutputStreamConfiguration config, final String fileName)
            throws IOException {
        super(BarjCargoOutputStreamConfiguration.builder()
                .folder(config.getFolder())
                .prefix(fileName)
                .compressionFunction(config.getCompressionFunction())
                .hashAlgorithm(config.getHashAlgorithm())
                .build());
    }

    /**
     * Returns an input stream for the given part of the archived entity.
     *
     * @param boundaries defines the boundaries of the part
     * @return an input stream
     * @throws IOException If the input stream cannot be created
     */
    public InputStream getStream(
            @NonNull final BarjCargoEntryBoundaries boundaries) throws IOException {
        final var start = boundaries.getAbsoluteStartIndexInclusive();
        final var length = boundaries.getAbsoluteEndIndexExclusive() - start;
        return new FixedRangeInputStream(new MergingFileInputStream(getDataFilesWritten()),
                start, length);
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
