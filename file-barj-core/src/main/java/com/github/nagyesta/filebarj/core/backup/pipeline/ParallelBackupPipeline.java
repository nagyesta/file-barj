package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
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
     * @param manifestDatabase The manifest database
     * @param threadCount      The number of threads
     * @throws IOException When the stream cannot be created due to an I/O error
     */
    public ParallelBackupPipeline(final @NotNull ManifestDatabase manifestDatabase,
                                  final int threadCount) throws IOException {
        super(manifestDatabase, convert(manifestDatabase, threadCount));
    }

    private static @NonNull ParallelBarjCargoArchiverFileOutputStream convert(
            final @NonNull ManifestDatabase manifestDatabase, final int threadCount) throws IOException {
        final var configuration = manifestDatabase.getLatestConfiguration();
        return new ParallelBarjCargoArchiverFileOutputStream(
                BarjCargoOutputStreamConfiguration.builder()
                        .folder(configuration.getDestinationDirectory())
                        .prefix(manifestDatabase.getLatestFileNamePrefix())
                        .compressionFunction(configuration.getCompression()::decorateOutputStream)
                        .indexEncryptionKey(manifestDatabase.getLatestDataIndexEncryptionKey())
                        .hashAlgorithm(configuration.getHashAlgorithm().getAlgorithmName())
                        .maxFileSizeMebibyte(configuration.getChunkSizeMebibyte())
                        .build(), threadCount);
    }

    /**
     * Stores the given files in the archive.
     *
     * @param groupedFileMetadataList The list of file metadata we should store
     * @return the list of archived files
     * @throws ArchivalException When the file cannot be archived due to an I/O error from the stream
     */
    public List<ArchivedFileMetadata> storeEntries(
            final @NonNull List<List<FileMetadata>> groupedFileMetadataList) throws ArchivalException {
        final var fileCount = groupedFileMetadataList.stream().filter(Objects::nonNull).mapToInt(List::size).sum();
        final var entryCount = groupedFileMetadataList.size();
        log.info("Storing the file content of {} entries ({} files) in parallel", entryCount, fileCount);
        final var list = groupedFileMetadataList.stream().map(fileMetadataList -> {
            if (fileMetadataList == null || fileMetadataList.isEmpty()) {
                throw new IllegalArgumentException("File metadata list cannot be null or empty");
            }
            final var fileMetadata = fileMetadataList.get(0);
            try {
                log.debug("Storing {}", fileMetadata.getAbsolutePath());
                fileMetadata.assertContentSource();
                final var archivedFileMetadata = generateArchiveFileMetadata(fileMetadata);
                return archiveContentAndUpdateMetadata(fileMetadata, archivedFileMetadata)
                        .thenApply(archived -> {
                            fileMetadataList.stream().skip(1).forEach(duplicate -> {
                                warnIfHashDoesNotMatch(duplicate, archived);
                                duplicate.setArchiveMetadataId(archived.getId());
                                archived.getFiles().add(duplicate.getId());
                                reportProgress(duplicate);
                            });
                            return archived;
                        });
            } catch (final Exception e) {
                log.error("Failed to store {}", fileMetadata.getAbsolutePath(), e);
                throw new ArchivalException("Failed to store " + fileMetadata.getAbsolutePath(), e);
            }
        }).toList();
        log.info("Asked for archival of {} entries asynchronously.", list.size());
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
        //noinspection resource
        final var encryptionKey = manifestDatabase().getDataEncryptionKey(archivedFileMetadata.getArchiveLocation());
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
            warnIfHashDoesNotMatch(fileMetadata, archivedFileMetadata);
            //commit
            fileMetadata.setArchiveMetadataId(archivedFileMetadata.getId());
            reportProgress(fileMetadata);
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
