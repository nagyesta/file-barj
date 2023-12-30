package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoBoundarySource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Provides a convenient API for the backup execution.
 *
 * @param <T> The type of the stream
 */
@Slf4j
public class BaseBackupPipeline<T extends BarjCargoArchiverFileOutputStream> implements AutoCloseable {

    private final BackupIncrementManifest manifest;
    private final T outputStream;

    /**
     * Creates a new instance for the manifest that must be used for the backup.
     *
     * @param manifest     The manifest
     * @param outputStream The stream to write to
     */
    protected BaseBackupPipeline(
            @NotNull final BackupIncrementManifest manifest,
            @NotNull final T outputStream) {
        this.manifest = manifest;
        this.outputStream = outputStream;
        manifest.getVersions().forEach(version -> {
            try {
                outputStream.addDirectoryEntity("/" + version, null);
            } catch (final IOException e) {
                throw new IllegalArgumentException("Cannot add directory entity", e);
            }
        });
    }

    /**
     * Returns the list of data files written during backup.
     *
     * @return the list of data files
     */
    public List<Path> getDataFilesWritten() {
        return outputStream.getDataFilesWritten();
    }

    /**
     * Returns the data index file written during backup.
     *
     * @return the data index file
     */
    public Path getIndexFileWritten() {
        return outputStream.getIndexFileWritten();
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
        return fileMetadataList.stream().map(fileMetadata -> {
            if (fileMetadata == null) {
                throw new IllegalArgumentException("File metadata cannot be null");
            }
            try {
                log.debug("Storing {}", fileMetadata.getAbsolutePath());
                fileMetadata.assertContentSource();
                final var archivedFileMetadata = generateArchiveFileMetadata(fileMetadata);
                archiveContentAndUpdateMetadata(fileMetadata, archivedFileMetadata);
                return archivedFileMetadata;
            } catch (final Exception e) {
                log.error("Failed to store {}", fileMetadata.getAbsolutePath(), e);
                throw new ArchivalException("Failed to store " + fileMetadata.getAbsolutePath(), e);
            }
        }).toList();
    }

    /**
     * Closes the streams opened by this instance.
     *
     * @throws Exception When the stream cannot be closed due to an I/O error
     */
    @SuppressWarnings("RedundantThrows")
    @Override
    public void close() throws Exception {
        IOUtils.closeQuietly(outputStream);
    }

    /**
     * Returns the manifest used for the backup.
     *
     * @return the manifest
     */
    protected BackupIncrementManifest manifest() {
        return manifest;
    }

    /**
     * Returns the stream used for the backup.
     *
     * @return the stream
     */
    protected T outputStream() {
        return outputStream;
    }

    /**
     * Generates an {@link ArchivedFileMetadata} for the given {@link FileMetadata}.
     *
     * @param fileMetadata The file metadata
     * @return the generated metadata
     */
    protected ArchivedFileMetadata generateArchiveFileMetadata(final FileMetadata fileMetadata) {
        final var archiveId = UUID.randomUUID();
        return ArchivedFileMetadata.builder()
                .id(archiveId)
                .files(new HashSet<>(Set.of(fileMetadata.getId())))
                .archiveLocation(createArchiveEntryLocator(archiveId))
                .build();
    }

    private void archiveContentAndUpdateMetadata(
            final FileMetadata fileMetadata,
            final ArchivedFileMetadata archivedFileMetadata) throws IOException {
        final var entryName = archivedFileMetadata.getArchiveLocation().asEntryPath();
        final var encryptionKey = manifest.dataEncryptionKey(archivedFileMetadata.getArchiveLocation());
        final BarjCargoBoundarySource source;
        if (fileMetadata.getFileType() == FileType.REGULAR_FILE) {
            source = outputStream.addFileEntity(entryName, fileMetadata.streamContent(), encryptionKey);
        } else {
            //noinspection resource
            final var targetPath = new String(fileMetadata.streamContent().readAllBytes(), StandardCharsets.UTF_8);
            source = outputStream.addSymbolicLinkEntity(entryName, targetPath, encryptionKey);
        }
        archivedFileMetadata.setOriginalHash(source.getContentBoundary().getOriginalHash());
        archivedFileMetadata.setArchivedHash(source.getContentBoundary().getArchivedHash());
        if (!Objects.equals(archivedFileMetadata.getOriginalHash(), fileMetadata.getOriginalHash())) {
            log.warn("The hash changed between delta calculation and archival for: " + fileMetadata.getAbsolutePath()
                    + "The archive might contain corrupt data for the file.");
        }
        //commit
        fileMetadata.setArchiveMetadataId(archivedFileMetadata.getId());
    }

    private ArchiveEntryLocator createArchiveEntryLocator(final UUID archiveId) {
        return ArchiveEntryLocator.builder()
                .entryName(archiveId)
                .backupIncrement(manifest.getVersions().last())
                .build();
    }
}
