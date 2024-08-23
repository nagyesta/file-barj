package com.github.nagyesta.filebarj.core.inspect.pipeline;

import com.github.nagyesta.filebarj.core.common.ManifestManager;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.inspect.worker.ManifestToSummaryConverter;
import com.github.nagyesta.filebarj.core.inspect.worker.TabSeparatedBackupContentExporter;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.progress.ObservableProgressTracker;
import com.github.nagyesta.filebarj.core.progress.ProgressStep;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
     * @param parameters The parameters.
     */
    public IncrementInspectionController(
            final @NonNull InspectParameters parameters) {
        final var progressTracker = new ObservableProgressTracker(List.of(ProgressStep.LOAD_MANIFESTS));
        progressTracker.registerListener(parameters.getProgressListener());
        final ManifestManager manifestManager = new ManifestManagerImpl(progressTracker);
        this.manifests = manifestManager
                .loadAll(parameters.getBackupDirectory(), parameters.getFileNamePrefix(), parameters.getKek());
    }

    /**
     * Selects the latest matching increment and writes its content to the specified output file.
     *
     * @param latestStartTimeEpochSeconds the start time of the latest allowed increment
     * @param outputFile                  the output file
     */
    public void inspectContent(
            final long latestStartTimeEpochSeconds,
            final @NonNull Path outputFile) {
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
    public void inspectIncrements(final @NonNull PrintStream outputStream) {
        final var manifestToSummaryConverter = new ManifestToSummaryConverter();
        manifests.forEach((key, value) -> outputStream.println(manifestToSummaryConverter.convertToSummaryString(value)));
    }
}
