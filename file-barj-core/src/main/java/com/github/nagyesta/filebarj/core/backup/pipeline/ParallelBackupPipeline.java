package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.BarjCargoBoundarySource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.ParallelBarjCargoArchiverFileOutputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Provides a convenient API for parallel backup execution.
 */
@Slf4j
public class ParallelBackupPipeline extends BaseBackupPipeline<ParallelBarjCargoArchiverFileOutputStream> {

    /**
     * Creates a new instance for the manifest that must be used for the backup.
     *
     * @param manifest    The manifest
     * @param threadCount The number of threads
     * @throws IOException When the stream cannot be created due to an I/O error
     */
    public ParallelBackupPipeline(@NotNull final BackupIncrementManifest manifest,
                                  final int threadCount) throws IOException {
        super(manifest, convert(manifest, threadCount));
    }

    @NonNull
    private static ParallelBarjCargoArchiverFileOutputStream convert(
            @NonNull final BackupIncrementManifest manifest, final int threadCount) throws IOException {
        return new ParallelBarjCargoArchiverFileOutputStream(
                BarjCargoOutputStreamConfiguration.builder()
                        .folder(manifest.getConfiguration().getDestinationDirectory())
                        .prefix(manifest.getFileNamePrefix())
                        .compressionFunction(manifest.getConfiguration().getCompression()::decorateOutputStream)
                        .indexEncryptionKey(manifest.dataIndexEncryptionKey())
                        .hashAlgorithm(manifest.getConfiguration().getHashAlgorithm().getAlgorithmName())
                        .maxFileSizeMebibyte(manifest.getConfiguration().getChunkSizeMebibyte())
                        .build(), threadCount);
    }

    /**
     * Stores the given files in the archive.
     *
     * @param fileMetadataList The list of file metadata we should store
     * @return the list of archived files
     * @throws ArchivalException When the file cannot be archived due to an I/O error from the stream
     */
    public List<ArchivedFileMetadata> storeEntries(
            @NonNull final List<FileMetadata> fileMetadataList) throws ArchivalException {
        final var list = fileMetadataList.stream().map(fileMetadata -> {
            if (fileMetadata == null) {
                throw new IllegalArgumentException("File metadata cannot be null");
            }
            try {
                log.debug("Storing {}", fileMetadata.getAbsolutePath());
                fileMetadata.assertContentSource();
                final var archivedFileMetadata = generateArchiveFileMetadata(fileMetadata);
                return archiveContentAndUpdateMetadata(fileMetadata, archivedFileMetadata);
            } catch (final Exception e) {
                log.error("Failed to store {}", fileMetadata.getAbsolutePath(), e);
                throw new ArchivalException("Failed to store " + fileMetadata.getAbsolutePath(), e);
            }
        }).toList();
        log.info("Asked for archival of {} files asynchronously.", list.size());
        final var result = new ArrayList<ArchivedFileMetadata>();
        for (final var future : list) {
            try {
                result.add(future.join());
            } catch (final CompletionException e) {
                unwrapArchivalException(e);
            }
        }
        return result;
    }

    private CompletableFuture<ArchivedFileMetadata> archiveContentAndUpdateMetadata(
            final FileMetadata fileMetadata,
            final ArchivedFileMetadata archivedFileMetadata) throws IOException {
        final var entryName = archivedFileMetadata.getArchiveLocation().asEntryPath();
        final var encryptionKey = manifest().dataEncryptionKey(archivedFileMetadata.getArchiveLocation());
        final CompletableFuture<BarjCargoBoundarySource> futureSource;
        if (fileMetadata.getFileType() == FileType.REGULAR_FILE) {
            futureSource = outputStream().addFileEntityAsync(entryName, fileMetadata.streamContent(), encryptionKey);
        } else {
            //noinspection resource
            final var targetPath = new String(fileMetadata.streamContent().readAllBytes(), StandardCharsets.UTF_8);
            futureSource = outputStream().addSymbolicLinkEntityAsync(entryName, targetPath, encryptionKey);
        }
        return futureSource.thenApplyAsync(boundarySource -> {
            archivedFileMetadata.setOriginalHash(boundarySource.getContentBoundary().getOriginalHash());
            archivedFileMetadata.setArchivedHash(boundarySource.getContentBoundary().getArchivedHash());
            if (!Objects.equals(archivedFileMetadata.getOriginalHash(), fileMetadata.getOriginalHash())) {
                log.warn("The hash changed between delta calculation and archival for: " + fileMetadata.getAbsolutePath()
                        + " The archive might contain corrupt data for the file.");
            }
            //commit
            fileMetadata.setArchiveMetadataId(archivedFileMetadata.getId());
            return archivedFileMetadata;
        });
    }

    private static void unwrapArchivalException(final CompletionException ex) throws ArchivalException {
        try {
            throw ex.getCause();
        } catch (final Throwable cause) {
            throw new ArchivalException("Archival failed.", cause);
        }
    }
}
