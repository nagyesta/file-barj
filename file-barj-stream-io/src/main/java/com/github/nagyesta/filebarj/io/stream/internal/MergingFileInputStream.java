package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoSupplier;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads data previously chunked by a {@link ChunkingFileOutputStream}.
 */
public class MergingFileInputStream extends MergingInputStream {

    /**
     * Creates a new instance and sets the parameters needed reading chunked file contents.
     *
     * @param folder    The folder where the files should be saved
     * @param prefix    The prefix of the file name in the folder
     * @param extension The suffix/extension of the file name (recommended to start with a '.')
     * @throws IOException If we cannot create the folder or write to it.
     */
    public MergingFileInputStream(
            final @NotNull Path folder, final @NotNull String prefix, final @NotNull String extension)
            throws IOException {
        //noinspection resource
        this(Files.list(folder)
                .filter(p -> {
                    final var fileName = p.getFileName().toString();
                    return fileName.matches("^" + Pattern.quote(prefix) + ".+" + Pattern.quote(extension) + "$");
                }).toList());
    }

    /**
     * Creates a new instance and sets the parameters needed reading chunked file contents.
     *
     * @param allFiles The list of all files to be read.
     * @throws IOException If the streams cannot be open.
     */
    public MergingFileInputStream(
            final @NotNull List<Path> allFiles) throws IOException {
        super(allFiles.stream()
                        .sorted(Comparator.comparing(Path::toAbsolutePath))
                        .map(path -> (IoSupplier<InputStream>) () -> new FileInputStream(path.toFile()))
                        .toList(),
                allFiles.stream()
                        .map(Path::toFile)
                        .mapToLong(File::length)
                        .sum());
    }
}
