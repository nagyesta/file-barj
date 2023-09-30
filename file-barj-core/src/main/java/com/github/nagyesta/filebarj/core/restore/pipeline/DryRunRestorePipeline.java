package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.RestoreManifest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.List;

/**
 * Special pipeline implementation for dry-run restore. Tha actual restore process is not executed,
 * but log messages are printed indicating what would happen during a real restore.
 */
@Slf4j
public class DryRunRestorePipeline extends RestorePipeline {

    /**
     * Creates a new pipeline instance for the specified manifests.
     *
     * @param manifest        the manifest
     * @param backupDirectory the directory where the backup files are located
     * @param restoreTargets  the mappings of the root paths where we would like to restore
     * @param kek             the key encryption key we would like to use to decrypt files
     */
    public DryRunRestorePipeline(@NotNull final RestoreManifest manifest,
                                 @NotNull final Path backupDirectory,
                                 @NotNull final RestoreTargets restoreTargets,
                                 @Nullable final PrivateKey kek) {
        super(manifest, backupDirectory, restoreTargets, kek);
    }

    @Override
    public void evaluateRestoreSuccess(@NotNull final List<FileMetadata> files) {
        //no-op
    }

    @Override
    protected void setFileProperties(@NotNull final FileMetadata fileMetadata) {
        log.info("> Set file properties for {}", fileMetadata.getAbsolutePath());
    }

    @Override
    protected void createSymbolicLink(
            @NotNull final Path linkTarget,
            @NotNull final Path symbolicLink) throws IOException {
        log.info("+ Create symbolic link {} -> {}", symbolicLink, linkTarget);
    }

    @Override
    protected void copyRestoredFileToRemainingLocations(
            @NotNull final FileMetadata original,
            @NotNull final List<FileMetadata> remainingCopies) {
        final var unpackedPath = getRestoreTargets().mapToRestorePath(original.getAbsolutePath());
        remainingCopies.forEach(file -> {
            final var copy = getRestoreTargets().mapToRestorePath(file.getAbsolutePath());
            if (file.getFileSystemKey().equals(original.getFileSystemKey())) {
                log.info("+ Create hard link {} -> {}", copy, unpackedPath);
            } else {
                log.info("+ Create file {} copied from {}", copy, unpackedPath);
            }
        });
    }

    @Override
    protected void createDirectory(@NotNull final Path path) throws IOException {
        if (!Files.exists(path)) {
            log.info("+ Create directory {}", path);
        }
    }

    @Override
    protected void restoreFileContent(
            @NotNull final InputStream content,
            @NotNull final Path target) {
        log.info("+ Create file {}", target);
        try {
            content.readAllBytes();
        } catch (final IOException e) {
            throw new ArchivalException("Failed to read file content", e);
        }
    }

    @Override
    protected void deleteIfExists(@NotNull final Path currentFile) throws IOException {
        if (Files.exists(currentFile)) {
            if (Files.isDirectory(currentFile)) {
                log.info("- Delete directory {}", currentFile);
            } else {
                log.info("- Delete file {}", currentFile);
            }
        }
    }
}
