package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.BaseBarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.internal.TempBarjCargoArchiverFileOutputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A file output stream that can write the data into temp files in parallel and merge them into the
 * main stream asynchronously.
 */
@Slf4j
public class ParallelBarjCargoArchiverFileOutputStream extends BarjCargoArchiverFileOutputStream {

    private static final String TEMP_DIR_NAME = "temp" + UUID.randomUUID();
    private static final String SLASH = "/";
    private final BarjCargoOutputStreamConfiguration tempFileConfig;
    private final ExecutorService mergingExecutor;
    private final ExecutorService splittingExecutor;
    private final Map<String, FileType> asyncEntityPaths = new ConcurrentHashMap<>();

    /**
     * Creates a new instance and sets the parameters needed for the BaRJ cargo streaming archival
     * file operations.
     *
     * @param config  The configuration for the BaRJ cargo archive
     * @param threads The number of threads to use for the archival
     * @throws IOException If we cannot create the folder or write to it.
     */
    public ParallelBarjCargoArchiverFileOutputStream(
            @NotNull final BarjCargoOutputStreamConfiguration config, final int threads)
            throws IOException {
        super(config);
        this.tempFileConfig = BarjCargoOutputStreamConfiguration.builder()
                .folder(config.getFolder().resolve(TEMP_DIR_NAME))
                .prefix(TEMP_DIR_NAME)
                .compressionFunction(config.getCompressionFunction())
                .hashAlgorithm(config.getHashAlgorithm())
                .build();
        this.mergingExecutor = Executors.newSingleThreadExecutor();
        this.splittingExecutor = Executors.newFixedThreadPool(threads);
    }

    /**
     * Adds a file entity to the archive asynchronously.
     *
     * @param path          the path of the entity inside the archive
     * @param contentStream A stream that contains the content of the entity.
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @return An object with the entity boundaries
     */
    public CompletableFuture<BarjCargoBoundarySource> addFileEntityAsync(
            @NotNull final String path, @NotNull final InputStream contentStream,
            @Nullable final SecretKey encryptionKey) {
        return addFileEntityAsync(path, contentStream, encryptionKey, null);
    }

    /**
     * Adds a file entity to the archive asynchronously.
     *
     * @param path          the path of the entity inside the archive
     * @param contentStream A stream that contains the content of the entity.
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @param metadata      The optional metadata of the entity (no metadata is added if null)
     * @return An object with the entity boundaries
     */
    public CompletableFuture<BarjCargoBoundarySource> addFileEntityAsync(
            @NotNull final String path, @NonNull final InputStream contentStream,
            @Nullable final SecretKey encryptionKey, @Nullable final String metadata) {
        normalizeAndValidateUniquePathForAsyncCalls(path, FileType.REGULAR_FILE);
        final var tempStream = new AtomicReference<TempBarjCargoArchiverFileOutputStream>();
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Adding file entity {}", path);
            try (var stream = new TempBarjCargoArchiverFileOutputStream(tempFileConfig, UUID.randomUUID().toString())) {
                autoCreateDirectories(path, stream);
                final var entity = stream.addFileEntity(path, contentStream, encryptionKey, metadata);
                stream.close();
                tempStream.set(stream);
                return entity;
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        }, splittingExecutor).thenApplyAsync(mergeEntityFromTempStreamAsync(tempStream));
    }

    /**
     * Adds a symbolic link entity to the archive asynchronously.
     *
     * @param path           The path of the entity inside the archive
     * @param linkTargetPath The target path of the symbolic link on the filesystem
     * @param encryptionKey  The optional encryption key (encryption is skipped if not provided)
     * @return An object with the entity boundaries
     */
    public CompletableFuture<BarjCargoBoundarySource> addSymbolicLinkEntityAsync(
            @NotNull final String path, @NotNull final String linkTargetPath,
            @Nullable final SecretKey encryptionKey) {
        return addSymbolicLinkEntityAsync(path, linkTargetPath, encryptionKey, null);
    }

    /**
     * Adds a symbolic link entity to the archive asynchronously.
     *
     * @param path           The path of the entity inside the archive
     * @param linkTargetPath The target path of the symbolic link on the filesystem
     * @param encryptionKey  The optional encryption key (encryption is skipped if not provided)
     * @param metadata       The optional metadata of the entity (no metadata is added if null)
     * @return An object with the entity boundaries
     */
    public CompletableFuture<BarjCargoBoundarySource> addSymbolicLinkEntityAsync(
            @NotNull final String path, @NonNull final String linkTargetPath,
            @Nullable final SecretKey encryptionKey, @Nullable final String metadata) {
        normalizeAndValidateUniquePathForAsyncCalls(path, FileType.SYMBOLIC_LINK);
        final var tempStream = new AtomicReference<TempBarjCargoArchiverFileOutputStream>();
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Adding symbolic link {} -> {}", path, linkTargetPath);
            try (var stream = new TempBarjCargoArchiverFileOutputStream(tempFileConfig, FilenameUtils.getName(path))) {
                autoCreateDirectories(path, stream);
                final var entity = stream.addSymbolicLinkEntity(path, linkTargetPath, encryptionKey, metadata);
                stream.close();
                tempStream.set(stream);
                return entity;
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        }, splittingExecutor).thenApplyAsync(mergeEntityFromTempStreamAsync(tempStream));
    }

    /**
     * Adds a directory entity to the archive asynchronously.
     *
     * @param path          The path of the entity inside the archive
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @return An object with the entity boundaries
     */
    public CompletableFuture<BarjCargoBoundarySource> addDirectoryEntityAsync(
            @NotNull final String path, @Nullable final SecretKey encryptionKey) {
        return addDirectoryEntityAsync(path, encryptionKey, null);
    }

    /**
     * Adds a directory entity to the archive asynchronously.
     *
     * @param path          The path of the entity inside the archive
     * @param encryptionKey The optional encryption key (encryption is skipped if not provided)
     * @param metadata      The optional metadata of the entity (no metadata is added if null)
     * @return An object with the entity boundaries
     */
    public CompletableFuture<BarjCargoBoundarySource> addDirectoryEntityAsync(
            @NotNull final String path, @Nullable final SecretKey encryptionKey,
            @Nullable final String metadata) {
        normalizeAndValidateUniquePathForAsyncCalls(path, FileType.DIRECTORY);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return super.addDirectoryEntity(path, encryptionKey, metadata);
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        }, mergingExecutor);
    }

    /**
     * Merges the content and metadata streams of the given entity into this stream.
     *
     * @param boundaryMetadata         The metadata of the entity
     * @param contentAndMetadataStream The stream containing the content stream and metadata stream
     *                                 one after the other
     * @return The boundary of the entity
     */
    public CompletableFuture<BarjCargoBoundarySource> mergeEntityAsync(
            @NonNull final BarjCargoBoundarySource boundaryMetadata,
            @NonNull final InputStream contentAndMetadataStream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return super.mergeEntity(boundaryMetadata, contentAndMetadataStream);
            } catch (final IOException e) {
                throw new CompletionException(e);
            }
        }, mergingExecutor);
    }

    @Override
    public BarjCargoBoundarySource addFileEntity(
            @NotNull final String path, @NotNull final InputStream contentStream,
            @Nullable final SecretKey encryptionKey)
            throws IOException {
        try {
            return this.addFileEntityAsync(path, contentStream, encryptionKey).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public BarjCargoBoundarySource addFileEntity(
            @NotNull final String path, @NotNull final InputStream contentStream,
            @Nullable final SecretKey encryptionKey, @Nullable final String metadata)
            throws IOException {
        try {
            return this.addFileEntityAsync(path, contentStream, encryptionKey, metadata).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public BarjCargoBoundarySource addSymbolicLinkEntity(
            @NotNull final String path, @NotNull final String linkTargetPath,
            @Nullable final SecretKey encryptionKey)
            throws IOException {
        try {
            return this.addSymbolicLinkEntityAsync(path, linkTargetPath, encryptionKey).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public BarjCargoBoundarySource addSymbolicLinkEntity(
            @NotNull final String path, @NotNull final String linkTargetPath,
            @Nullable final SecretKey encryptionKey, @Nullable final String metadata)
            throws IOException {
        try {
            return this.addSymbolicLinkEntityAsync(path, linkTargetPath, encryptionKey, metadata).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public BarjCargoBoundarySource addDirectoryEntity(
            @NotNull final String path,
            @Nullable final SecretKey encryptionKey) throws IOException {
        try {
            return this.addDirectoryEntityAsync(path, encryptionKey).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public BarjCargoBoundarySource addDirectoryEntity(
            @NotNull final String path,
            @Nullable final SecretKey encryptionKey,
            @Nullable final String metadata) throws IOException {
        try {
            return this.addDirectoryEntityAsync(path, encryptionKey, metadata).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public BarjCargoBoundarySource mergeEntity(
            @NotNull final BarjCargoBoundarySource boundaryMetadata,
            @NotNull final InputStream contentAndMetadataStream) throws IOException {
        try {
            return this.mergeEntityAsync(boundaryMetadata, contentAndMetadataStream).join();
        } catch (final CompletionException ex) {
            unwrapIoException(ex);
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        splittingExecutor.shutdown();
        mergingExecutor.shutdown();
        super.close();
        Files.deleteIfExists(tempFileConfig.getFolder());
    }

    private void normalizeAndValidateUniquePathForAsyncCalls(
            @NotNull final String path, @NotNull final FileType fileType) {
        final var entityPath = normalizeEntityPath(path);
        assertEntityNameIsValidAndUnique(asyncEntityPaths, entityPath, fileType);
    }

    private void unwrapIoException(final CompletionException ex) throws IOException {
        try {
            throw ex.getCause();
        } catch (final IOException | IllegalArgumentException expected) {
            throw expected;
        } catch (final Throwable unknown) {
            throw new IllegalStateException("Unexpected exception: ", unknown);
        }
    }

    @NotNull
    private Function<BarjCargoBoundarySource, BarjCargoBoundarySource> mergeEntityFromTempStreamAsync(
            @NotNull final AtomicReference<TempBarjCargoArchiverFileOutputStream> tempStream) {
        return entity -> {
            try {
                final var stream = tempStream.get();
                if (stream == null) {
                    throw new IllegalStateException("Temporary stream is null for " + entity.getPath());
                }
                log.debug("Merging temp file {} into {}", stream.getCurrentFilePath(), entity.getPath());
                super.mergeEntity(entity, stream.getStream(entity.getContentBoundary(), entity.getMetadataBoundary()));
                log.debug("Merged temp file {} into {}", stream.getCurrentFilePath(), entity.getPath());
                return entity;
            } catch (final Exception e) {
                throw new CompletionException(e);
            } finally {
                Optional.ofNullable(tempStream.get()).ifPresent(stream -> {
                    IOUtils.closeQuietly(stream);
                    try {
                        stream.delete();
                    } catch (final IOException ignore) {
                        // ignore
                    }
                });
            }
        };
    }

    private void autoCreateDirectories(
            @NotNull final String path,
            @NotNull final BaseBarjCargoArchiverFileOutputStream stream) throws IOException {
        final var normalizedPath = normalizeEntityPath(path);
        if (normalizedPath != null) {
            final var tokens = normalizedPath.split(SLASH);
            for (var i = 2; i < tokens.length; i++) {
                final var folder = Arrays.stream(tokens).limit(i).collect(Collectors.joining(SLASH));
                stream.addDirectoryEntity(folder, null);
            }
        }
    }
}
