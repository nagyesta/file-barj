package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.BarjCargoBoundarySource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.BasicBarjCargoBoundarySource;
import com.github.nagyesta.filebarj.io.stream.IoFunction;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.commons.io.FilenameUtils.normalizeNoEndSeparator;
import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

/**
 * A FileOutputStream that can produce archives using the BaRJ cargo format.
 */
@Slf4j
public class BaseBarjCargoArchiverFileOutputStream extends ChunkingFileOutputStream {

    @Getter
    private final IoFunction<OutputStream, OutputStream> compressionFunction;
    @Getter
    private final String hashAlgorithm;
    private BarjCargoEntityArchiver openEntity;
    private final Map<String, FileType> entityPaths = new ConcurrentHashMap<>();
    private final ReentrantLock entityLock = new ReentrantLock();
    private boolean closed;
    private final ReentrantLock closeLock = new ReentrantLock();
    private final AtomicLong entryCount = new AtomicLong(0L);

    /**
     * Creates a new instance and sets the parameters needed for the BaRJ cargo streaming archival
     * file operations.
     *
     * @param config The configuration for the BaRJ cargo archive
     * @throws IOException If we cannot create the folder or write to it.
     */
    public BaseBarjCargoArchiverFileOutputStream(
            @NotNull final BarjCargoOutputStreamConfiguration config) throws IOException {
        super(config.getFolder(), config.getPrefix(), config.getMaxFileSizeMebibyte());
        this.compressionFunction = config.getCompressionFunction();
        this.hashAlgorithm = config.getHashAlgorithm();
    }

    /**
     * Adds a file entity to the archive. Automatically calls all lifecycle methods to ensure
     * that the entity streams are properly closed.
     *
     * @param path          the path of the entity inside the archive
     * @param contentStream A stream that contains the content of the entity.
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource addFileEntity(
            @NotNull final String path,
            @NotNull final InputStream contentStream,
            @Nullable final SecretKey encryptionKey) throws IOException {
        return addFileEntity(path, contentStream, encryptionKey, null);
    }

    /**
     * Adds a file entity to the archive. Automatically calls all lifecycle methods to ensure
     * that the entity streams are properly closed.
     *
     * @param path          the path of the entity inside the archive
     * @param contentStream A stream that contains the content of the entity.
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @param metadata      The optional metadata of the entity (no metadata is added if null)
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource addFileEntity(
            @NotNull final String path,
            @NonNull final InputStream contentStream,
            @Nullable final SecretKey encryptionKey,
            @Nullable final String metadata) throws IOException {
        try (var entity = openEntity(path, FileType.REGULAR_FILE, encryptionKey)) {
            writeContent(contentStream);
            writeMetadata(metadata);
            closeCurrentEntity();
            return entity;
        }
    }

    /**
     * Adds a symbolic link entity to the archive. Automatically calls all lifecycle methods to
     * ensure that the entity streams are properly closed.
     *
     * @param path           The path of the entity inside the archive
     * @param linkTargetPath The target path of the symbolic link on the filesystem
     * @param encryptionKey  The optional encryption key (encryption is skipped if not provided)
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource addSymbolicLinkEntity(
            @NotNull final String path,
            @NotNull final String linkTargetPath,
            @Nullable final SecretKey encryptionKey) throws IOException {
        return addSymbolicLinkEntity(path, linkTargetPath, encryptionKey, null);
    }

    /**
     * Adds a symbolic link entity to the archive. Automatically calls all lifecycle methods to
     * ensure that the entity streams are properly closed.
     *
     * @param path           The path of the entity inside the archive
     * @param linkTargetPath The target path of the symbolic link on the filesystem
     * @param encryptionKey  The optional encryption key (encryption is skipped if not provided)
     * @param metadata       The optional metadata of the entity (no metadata is added if null)
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource addSymbolicLinkEntity(
            @NotNull final String path,
            @NonNull final String linkTargetPath,
            @Nullable final SecretKey encryptionKey,
            @Nullable final String metadata) throws IOException {
        try (var entity = openEntity(path, FileType.SYMBOLIC_LINK, encryptionKey)) {
            writeContent(new ByteArrayInputStream(linkTargetPath.getBytes(StandardCharsets.UTF_8)));
            writeMetadata(metadata);
            closeCurrentEntity();
            return entity;
        }
    }

    /**
     * Adds a directory entity to the archive. Automatically calls all lifecycle methods to ensure
     * that the entity streams are properly closed.
     *
     * @param path          The path of the entity inside the archive
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource addDirectoryEntity(
            @NotNull final String path,
            @Nullable final SecretKey encryptionKey) throws IOException {
        return addDirectoryEntity(path, encryptionKey, null);
    }

    /**
     * Adds a directory entity to the archive. Automatically calls all lifecycle methods to ensure
     * that the entity streams are properly closed.
     *
     * @param path          The path of the entity inside the archive
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @param metadata      The optional metadata of the entity (no metadata is added if null)
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource addDirectoryEntity(
            @NotNull final String path,
            @Nullable final SecretKey encryptionKey,
            @Nullable final String metadata) throws IOException {
        try (var entity = openEntity(path, FileType.DIRECTORY, encryptionKey)) {
            writeMetadata(metadata);
            closeCurrentEntity();
            return entity;
        }
    }

    /**
     * Merges an entity from another stream into this stream.
     *
     * @param boundaryMetadata The metadata of the entity
     * @param contentStream    The content stream (null when the entity is a directory)
     * @param metadataStream   The metadata stream
     * @return An object with the entity boundaries
     * @throws IOException When an IO exception occurs during the write operation
     */
    public BarjCargoBoundarySource mergeEntity(
            @NonNull final BarjCargoBoundarySource boundaryMetadata,
            @Nullable final InputStream contentStream,
            @NonNull final InputStream metadataStream) throws IOException {
        if (this.hasOpenEntity()) {
            throw new IllegalStateException("Entity is already open.");
        }
        entityLock.lock();
        try {
            if (this.hasOpenEntity()) {
                throw new IllegalStateException("Entity is already open.");
            }
            final var fileType = boundaryMetadata.getFileType();
            final var entityPath = normalizeAndValidateUniquePath(boundaryMetadata.getPath(), fileType);
            @SuppressWarnings("DataFlowIssue") final var resultBuilder = BasicBarjCargoBoundarySource.builder()
                    .path(entityPath)
                    .fileType(fileType)
                    .encrypted(boundaryMetadata.isEncrypted());
            if (fileType != FileType.DIRECTORY) {
                if (contentStream == null) {
                    throw new IllegalArgumentException("Content stream must not be null.");
                }
                final var boundaries = mergePart(boundaryMetadata.getContentBoundary(), contentStream);
                resultBuilder.contentBoundary(boundaries);
            }
            final var boundaries = mergePart(boundaryMetadata.getMetadataBoundary(), metadataStream);
            resultBuilder.metadataBoundary(boundaries);
            final var boundarySource = resultBuilder.build();
            this.entryCount.incrementAndGet();
            this.doOnEntityClosed(getEntityToIndex(boundarySource));
            return boundarySource;
        } finally {
            entityLock.unlock();
        }
    }

    @Override
    public void flush() throws IOException {
        super.flush();
    }

    /**
     * Closes the current entity as well as every open stream opened implicitly by creating this
     * entity.
     *
     * @throws IOException If the entity could not be closed due to an I/O exception.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closeCurrentEntity();
            super.close();
            this.doOnClosed();
            closed = true;
        } finally {
            closeLock.unlock();
        }
    }

    /**
     * Normalizes and validates the given path.
     *
     * @param path     The path to validate
     * @param fileType The file type
     * @return The normalized and validated path
     */
    @Nullable
    protected String normalizeAndValidateUniquePath(
            @NotNull final String path, @NotNull final FileType fileType) {
        final var entityPath = normalizeEntityPath(path);
        assertEntityNameIsValidAndUnique(entityPaths, entityPath, fileType);
        return entityPath;
    }

    protected void doOnClosed() throws IOException {

    }

    /**
     * Returns whether there is an open entity is active on the stream.
     *
     * @return true if there is an open entity, false otherwise
     */
    public boolean hasOpenEntity() {
        return this.openEntity != null;
    }

    /**
     * Returns the number of entries in the archive.
     *
     * @return the number of entries
     */
    protected long entryCount() {
        return entryCount.get();
    }

    /**
     * Opens a new entity for archival and sets the required parameters.
     * <br/>
     * <b>WARNING:</b> This is not the most convent way to manage an entity. It is recommended to use
     * one of the linked add.* methods instead, unless you need to use this lower level API.
     *
     * @param archiveEntityPath The path of the entity inside the archive
     * @param fileType          The type of the entity
     * @param encryptionKey     The optional encryption key (encryption is skipped if not provided)
     * @return the opened entity
     * @see #addFileEntity(String, InputStream, SecretKey)
     * @see #addFileEntity(String, InputStream, SecretKey, String)
     * @see #addSymbolicLinkEntity(String, String, SecretKey)
     * @see #addSymbolicLinkEntity(String, String, SecretKey, String)
     * @see #addDirectoryEntity(String, SecretKey)
     * @see #addDirectoryEntity(String, SecretKey, String)
     */
    protected BarjCargoEntityArchiver openEntity(
            @NotNull final String archiveEntityPath, @NotNull final FileType fileType, @Nullable final SecretKey encryptionKey) {
        if (this.hasOpenEntity()) {
            throw new IllegalStateException("Entity is already open.");
        }
        entityLock.lock();
        try {
            if (this.hasOpenEntity()) {
                throw new IllegalStateException("Entity is already open.");
            }
            final var entityPath = normalizeAndValidateUniquePath(archiveEntityPath, fileType);
            this.openEntity = new BarjCargoEntityArchiver(entityPath, fileType, this, encryptionKey);
            this.entryCount.incrementAndGet();
            return this.openEntity;
        } finally {
            entityLock.unlock();
        }
    }

    /**
     * Closes the current open entity.
     *
     * @throws IOException If the entity could not be closed due to an exception.
     */
    protected void closeCurrentEntity() throws IOException {
        entityLock.lock();
        try {
            if (this.hasOpenEntity()) {
                this.openEntity.close();
                this.doOnEntityClosed(getEntityToIndex(openEntity));
                this.openEntity = null;
            }
        } finally {
            entityLock.unlock();
        }
    }

    /**
     * Performs some action exactly once when the close method is first called on the stream.
     *
     * @param entityToIndex the closed entity's to index
     * @throws IOException If the entity could not be closed due to an exception@
     */
    protected void doOnEntityClosed(@Nullable final BarjCargoEntityIndex entityToIndex) throws IOException {

    }

    @Nullable
    protected String normalizeEntityPath(final String archiveEntityPath) {
        return separatorsToUnix(normalizeNoEndSeparator(archiveEntityPath));
    }

    protected void assertEntityNameIsValidAndUnique(
            final Map<String, FileType> existingEntities, final String archiveEntityPath, final FileType fileType) {
        if (archiveEntityPath == null || archiveEntityPath.isBlank()) {
            throw new IllegalArgumentException("Entity name must not be null or blank.");
        }
        if (!archiveEntityPath.startsWith("/")) {
            throw new IllegalArgumentException("Entity name must start with '/'.");
        }
        if (existingEntities.containsKey(archiveEntityPath)) {
            throw new IllegalArgumentException("Entity name must be unique.");
        }
        final var lastSlash = archiveEntityPath.lastIndexOf("/");
        if (lastSlash != -1) {
            final var parentPath = archiveEntityPath.substring(0, lastSlash);
            if (!parentPath.isEmpty()) {
                if (!existingEntities.containsKey(parentPath)) {
                    throw new IllegalArgumentException("Parent entity must exist in archive.");
                }
                if (existingEntities.get(parentPath) != FileType.DIRECTORY) {
                    throw new IllegalArgumentException("Parent entity must not be a directory.");
                }
            }
        }
        existingEntities.put(archiveEntityPath, fileType);
    }

    private BarjCargoEntryBoundaries mergePart(
            @NotNull final BarjCargoEntryBoundaries boundary,
            @NonNull final InputStream stream) throws IOException {
        final var builder = BarjCargoEntryBoundaries.builder()
                .absoluteStartIndexInclusive(getTotalByteCount())
                .startChunkName(getCurrentFilePath().getFileName().toString())
                .chunkRelativeStartIndexInclusive(getChunkRelativeByteCount())
                .originalSizeBytes(boundary.getOriginalSizeBytes())
                .originalHash(boundary.getOriginalHash())
                .archivedSizeBytes(boundary.getArchivedSizeBytes())
                .archivedHash(boundary.getArchivedHash());
        stream.transferTo(this);
        stream.close();
        return builder.absoluteEndIndexExclusive(getTotalByteCount())
                .endChunkName(getCurrentFilePath().getFileName().toString())
                .chunkRelativeEndIndexExclusive(getChunkRelativeByteCount())
                .build();
    }

    private void writeContent(final InputStream contentInput) throws IOException {
        try (var contentStream = this.openEntity.openContentStream()) {
            IOUtils.copy(contentInput, contentStream);
        } finally {
            this.openEntity.closeContentStream();
        }
    }

    private void writeMetadata(final String metadata) throws IOException {
        try (var metadataStream = this.openEntity.openMetadataStream()) {
            if (metadata != null) {
                IOUtils.write(metadata.getBytes(StandardCharsets.UTF_8), metadataStream);
            }
        } finally {
            this.openEntity.closeMetadataStream();
        }
    }

    private BarjCargoEntityIndex getEntityToIndex(final BarjCargoBoundarySource openEntity) {
        try {
            return BarjCargoEntityIndex.builder()
                    .path(openEntity.getPath())
                    .fileType(openEntity.getFileType())
                    .encrypted(openEntity.isEncrypted())
                    .content(openEntity.getContentBoundary())
                    .metadata(openEntity.getMetadataBoundary())
                    .build();
        } catch (final IllegalArgumentException e) {
            log.warn("Couldn't close open entity.", e);
        }
        return null;
    }
}
