package com.github.nagyesta.filebarj.core.delete;

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
public class IncrementDeletionParameters {
    private final @NonNull Path backupDirectory;
    private final @NonNull String fileNamePrefix;
    private final @Nullable PrivateKey kek;
    @Builder.Default
    private final @NonNull ProgressListener progressListener = LoggingProgressListener.INSTANCE;
}
