package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.toChunkFileName;

/**
 * An implementation for {@link ChunkingOutputStream} that can write the data into multiple files
 * with a predefined maximum size.
 */
public class ChunkingFileOutputStream extends ChunkingOutputStream {
    /**
     * The number of bytes per mebibyte.
     */
    private final Path folder;
    private final String prefix;
    @Getter
    private Path currentFilePath;
    private final List<Path> dataFilesWritten = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(1);

    /**
     * Creates a new instance and sets the parameters needed for chunked
     * file operations.
     *
     * @param folder              The folder where the files should be saved
     * @param prefix              The prefix of the file name in the folder
     * @param maxFileSizeMebibyte The maximum file size in mebibyte
     * @throws IOException If we cannot create the folder or write to it.
     */
    public ChunkingFileOutputStream(
            @NonNull final Path folder,
            @NonNull final String prefix,
            final int maxFileSizeMebibyte)
            throws IOException {
        super(maxFileSizeMebibyte);
        this.folder = folder;
        this.prefix = prefix;
        createFolderIfNotExists();
        //noinspection resource
        openNextStream();

    }

    /**
     * Returns the list of files written by this stream.
     *
     * @return files written
     */
    @NotNull
    public List<Path> getDataFilesWritten() {
        return Collections.unmodifiableList(dataFilesWritten);
    }

    /**
     * Creates a new file with the given name inside the specified output folder and adds it to
     * the list of data files..
     *
     * @param fileName the name of the file
     * @return the path of the file
     * @throws IOException When the file cannot be created due ot an I/O exception
     */
    @NotNull
    protected Path createDataFile(final String fileName) throws IOException {
        final var path = doCreateFile(fileName);
        this.dataFilesWritten.add(path);
        return path;
    }

    /**
     * Creates a new file with the given name inside the specified output folder.
     *
     * @param fileName the name of the file
     * @return the path of the file
     * @throws IOException When the file cannot be created due ot an I/O exception
     */
    @NotNull
    protected Path doCreateFile(final String fileName) throws IOException {
        final var folderName = folder.toAbsolutePath().toString();
        final var path = Path.of(folderName, fileName);
        final var file = path.toFile();
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();
        return path;
    }

    /**
     * Returns the current chunk index.
     *
     * @return the current chunk index
     */
    public int getCurrentChunkIndex() {
        return counter.get() - 1;
    }

    @Override
    protected @NotNull OutputStream doOpenNextStream() throws IOException {
        if (currentFilePath != null) {
            final var actualSize = Files.size(currentFilePath);
            if (actualSize < getMaxChunkSizeBytes()) {
                throw new IllegalStateException(
                        "The chunk is being closed before the threshold was reached: " + currentFilePath.toString());
            }
        }
        final var fileName = toChunkFileName(prefix, counter.getAndIncrement());
        final var path = createDataFile(fileName);
        currentFilePath = path;
        final var fileOutputStream = new FileOutputStream(path.toFile());
        return new BufferedOutputStream(fileOutputStream);
    }

    private void createFolderIfNotExists() throws IOException {
        if (!folder.toFile().exists()) {
            Files.createDirectories(folder);
        }
    }
}
