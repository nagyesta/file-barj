package com.github.nagyesta.filebarj.job.cli;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.nio.file.Path;

/**
 * The parsed command line arguments of a key store generation task.
 */
@Data
@Builder
public class KeyStoreProperties {
    private final @NonNull Path keyStore;
    private final char[] password;
    @Builder.Default
    private final @NonNull String alias = "default";
}
