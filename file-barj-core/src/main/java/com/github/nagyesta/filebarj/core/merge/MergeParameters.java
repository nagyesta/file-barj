package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.progress.LoggingProgressListener;
import com.github.nagyesta.filebarj.core.progress.ProgressListener;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;

@Data
@Builder
public class MergeParameters {
    private final @NonNull Path backupDirectory;
    private final @NonNull String fileNamePrefix;
    private final @Nullable PrivateKey kek;
    private final long rangeStartEpochSeconds;
    private final long rangeEndEpochSeconds;
    @Builder.Default
    private final @NonNull ProgressListener progressListener = LoggingProgressListener.INSTANCE;

    public void assertValid() {
        if (rangeEndEpochSeconds <= rangeStartEpochSeconds) {
            throw new IllegalArgumentException(
                    "Invalid range selected for merge! start=" + rangeEndEpochSeconds + ", end=" + rangeStartEpochSeconds);
        }
    }
}
