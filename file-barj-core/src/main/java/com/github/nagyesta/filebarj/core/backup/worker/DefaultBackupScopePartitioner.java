package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base implementation of {@link BackupScopePartitioner}.
 */
public class DefaultBackupScopePartitioner implements BackupScopePartitioner {

    private final Function<FileMetadata, String> groupingFunction;

    /**
     * Creates a new instance with the specified batch size.
     *
     * @param duplicateHandlingStrategy the duplicate handling strategy
     * @param hashAlgorithm             the hash algorithm the backup is using
     */
    public DefaultBackupScopePartitioner(
            final @NonNull DuplicateHandlingStrategy duplicateHandlingStrategy,
            final @NonNull HashAlgorithm hashAlgorithm) {
        this.groupingFunction = duplicateHandlingStrategy.fileGroupingFunctionForHash(hashAlgorithm);
    }

    @Override
    public @NotNull List<List<FileMetadata>> partitionBackupScope(final @NonNull Collection<FileMetadata> scope) {
        return scope.stream()
                .filter(metadata -> metadata.getStatus().isStoreContent())
                .filter(metadata -> metadata.getFileType().isContentSource())
                .collect(Collectors.groupingBy(groupingFunction))
                .values().stream().toList();
    }
}
