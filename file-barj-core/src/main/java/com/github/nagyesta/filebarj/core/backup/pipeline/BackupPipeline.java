package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Provides a convenient API for the backup execution.
 */
@Slf4j
public class BackupPipeline extends BaseBackupPipeline<BarjCargoArchiverFileOutputStream> {

    /**
     * Creates a new instance for the manifest that must be used for the backup.
     *
     * @param manifest The manifest
     * @throws IOException When the stream cannot be created due to an I/O error
     */
    public BackupPipeline(final @NotNull BackupIncrementManifest manifest) throws IOException {
        super(manifest, convert(manifest));
    }

    private static @NonNull BarjCargoArchiverFileOutputStream convert(
            final @NonNull BackupIncrementManifest manifest) throws IOException {
        return new BarjCargoArchiverFileOutputStream(
                BarjCargoOutputStreamConfiguration.builder()
                        .folder(manifest.getConfiguration().getDestinationDirectory())
                        .prefix(manifest.getFileNamePrefix())
                        .compressionFunction(manifest.getConfiguration().getCompression()::decorateOutputStream)
                        .indexEncryptionKey(manifest.dataIndexEncryptionKey())
                        .hashAlgorithm(manifest.getConfiguration().getHashAlgorithm().getAlgorithmName())
                        .maxFileSizeMebibyte(manifest.getConfiguration().getChunkSizeMebibyte())
                        .build());
    }
}
