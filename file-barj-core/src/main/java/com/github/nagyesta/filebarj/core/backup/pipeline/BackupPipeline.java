package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
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
     * @param manifestDatabase The manifest database
     * @throws IOException When the stream cannot be created due to an I/O error
     */
    public BackupPipeline(final @NotNull ManifestDatabase manifestDatabase) throws IOException {
        super(manifestDatabase, convert(manifestDatabase));
    }

    private static @NonNull BarjCargoArchiverFileOutputStream convert(
            final @NonNull ManifestDatabase manifestDatabase) throws IOException {
        final var configuration = manifestDatabase.getLatestConfiguration();
        return new BarjCargoArchiverFileOutputStream(
                BarjCargoOutputStreamConfiguration.builder()
                        .folder(configuration.getDestinationDirectory())
                        .prefix(manifestDatabase.getLatestFileNamePrefix())
                        .compressionFunction(configuration.getCompression()::decorateOutputStream)
                        .indexEncryptionKey(manifestDatabase.getLatestDataIndexEncryptionKey())
                        .hashAlgorithm(configuration.getHashAlgorithm().getAlgorithmName())
                        .maxFileSizeMebibyte(configuration.getChunkSizeMebibyte())
                        .build());
    }
}
