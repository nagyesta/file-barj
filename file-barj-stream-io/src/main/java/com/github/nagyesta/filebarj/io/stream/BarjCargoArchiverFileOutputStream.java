package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.internal.BaseBarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.*;
import static com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil.newCipherOutputStream;

/**
 * The generic logic of a FileOutputStream that can produce archives using the BaRJ cargo format.
 */
@Slf4j
public class BarjCargoArchiverFileOutputStream extends BaseBarjCargoArchiverFileOutputStream {

    private final Path indexFile;
    private final FileOutputStream indexStream;
    private final BufferedOutputStream indexBufferedStream;
    private final OutputStream indexCompressionStream;
    private final OutputStream indexEncryptionStream;
    private final OutputStreamWriter indexStreamWriter;

    /**
     * Creates a new instance and sets the parameters needed for the BaRJ cargo streaming archival
     * file operations.
     *
     * @param config The configuration for the BaRJ cargo archive
     * @throws IOException If we cannot create the folder or write to it.
     */
    public BarjCargoArchiverFileOutputStream(
            @NotNull final BarjCargoOutputStreamConfiguration config) throws IOException {
        super(config);
        this.indexFile = doCreateFile(toIndexFileName(config.getPrefix()));
        this.indexStream = new FileOutputStream(indexFile.toFile());
        this.indexBufferedStream = new BufferedOutputStream(indexStream);
        this.indexEncryptionStream = newCipherOutputStream(config.getIndexEncryptionKey()).decorate(indexBufferedStream);
        this.indexCompressionStream = config.getCompressionFunction().decorate(indexEncryptionStream);
        this.indexStreamWriter = new OutputStreamWriter(indexCompressionStream, StandardCharsets.UTF_8);
        writeIndexFileHeader();
    }

    /**
     * Returns the path of the index file written by this entity.
     *
     * @return The path of the index file
     */
    public Path getIndexFileWritten() {
        return indexFile;
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        indexStreamWriter.flush();
        indexCompressionStream.flush();
        indexEncryptionStream.flush();
        indexBufferedStream.flush();
        indexStream.flush();
    }

    @Override
    protected void doOnClosed() throws IOException {
        super.doOnClosed();
        writeIndexFileFooter();
        IOUtils.closeQuietly(indexStreamWriter);
        IOUtils.closeQuietly(indexCompressionStream);
        IOUtils.closeQuietly(indexEncryptionStream);
        IOUtils.closeQuietly(indexBufferedStream);
        IOUtils.closeQuietly(indexStream);
    }

    @Override
    protected void doOnEntityClosed(final @Nullable BarjCargoEntityIndex entityToIndex) throws IOException {
        super.doOnEntityClosed(entityToIndex);
        if (entityToIndex != null) {
            writeEntityToIndex(entityToIndex);
        }
    }

    private void writeEntityToIndex(@NotNull final BarjCargoEntityIndex entityIndex) throws IOException {
        try {
            final var prefix = entryIndexPrefix(entryCount());
            this.indexStreamWriter.write(entityIndex.toProperties(prefix));
            this.indexStreamWriter.flush();
        } catch (final IllegalArgumentException e) {
            log.warn("Couldn't close open entity.", e);
        }
    }

    private void writeIndexFileHeader() throws IOException {
        indexStreamWriter.write("# File BaRJ Cargo Archive Index\n");
    }

    private void writeIndexFileFooter() throws IOException {
        final var lastChunk = getCurrentFilePath();
        final var footer = LAST_CHUNK_INDEX_PROPERTY + COLON + getCurrentChunkIndex() + LINE_BREAK
                + LAST_CHUNK_SIZE_PROPERTY + COLON + lastChunk.toFile().length() + LINE_BREAK
                + MAX_CHUNK_SIZE_PROPERTY + COLON + getMaxChunkSizeBytes() + LINE_BREAK
                + LAST_ENTITY_INDEX_PROPERTY + COLON + entryCount() + LINE_BREAK
                + TOTAL_SIZE_PROPERTY + COLON + getTotalByteCount() + LINE_BREAK;
        indexStreamWriter.write(footer);
    }
}
