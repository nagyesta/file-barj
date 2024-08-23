package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.progress.LoggingProgressListener;
import com.github.nagyesta.filebarj.core.progress.ProgressListener;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class BackupParameters {
    private final @NonNull BackupJobConfiguration job;
    @Builder.Default
    private final boolean forceFull = false;
    @Builder.Default
    private final @NonNull ProgressListener progressListener = LoggingProgressListener.INSTANCE;
}
