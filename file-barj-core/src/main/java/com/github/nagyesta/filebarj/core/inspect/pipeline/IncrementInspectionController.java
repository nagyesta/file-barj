package com.github.nagyesta.filebarj.core.inspect.pipeline;

import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.inspect.worker.ManifestToSummaryConverter;
import com.github.nagyesta.filebarj.core.inspect.worker.TabSeparatedBackupContentExporter;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.SortedMap;

/**
 * Controller for the backup increment inspection task.
 */
@Slf4j
public class IncrementInspectionController {

    private final SortedMap<Long, BackupIncrementManifest> manifests;

    /**
     * Creates a new instance and initializes it for the specified job.
     *
     * @param backupDirectory the directory where the backup files are located
     * @param fileNamePrefix  the prefix of the backup file names
     * @param kek             The key encryption key we want to use to decrypt the files (optional).
     *                        If null, no decryption will be performed.
     */
    public IncrementInspectionController(
            @NonNull final Path backupDirectory,
            @NonNull final String fileNamePrefix,
            @Nullable final PrivateKey kek) {
        final ManifestManager manifestManager = new ManifestManagerImpl();
        this.manifests = manifestManager.loadAll(backupDirectory, fileNamePrefix, kek);
    }

    /**
     * Selects the latest matching increment and writes its content to the specified output file.
     *
     * @param latestStartTimeEpochSeconds the start time of the latest allowed increment
     * @param outputFile                  the output file
     */
    public void inspectContent(
            final long latestStartTimeEpochSeconds,
            @NonNull final Path outputFile) {
        final var selectedUpperLimit = Math.min(Instant.now().getEpochSecond(), latestStartTimeEpochSeconds);
        final var relevant = this.manifests.headMap(selectedUpperLimit + 1).lastKey();
        log.info("Found increment with start timestamp: {}", relevant);
        final var manifest = this.manifests.get(relevant);
        new TabSeparatedBackupContentExporter().writeManifestContent(manifest, outputFile);
    }

    /**
     * Prints summary information about the manifests to the specified output stream.
     *
     * @param outputStream the output stream
     */
    public void inspectIncrements(@NonNull final PrintStream outputStream) {
        final var manifestToSummaryConverter = new ManifestToSummaryConverter();
        manifests.forEach((key, value) -> outputStream.println(manifestToSummaryConverter.convertToSummaryString(value)));
    }
}
