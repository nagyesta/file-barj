package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base implementation of {@link BackupScopePartitioner}.
 */
public class DefaultBackupScopePartitioner implements BackupScopePartitioner {

    private final int batchSize;
    private final Function<FileMetadata, String> groupingFunction;

    /**
     * Creates a new instance with the specified batch size.
     *
     * @param batchSize                 the batch size
     * @param duplicateHandlingStrategy the duplicate handling strategy
     * @param hashAlgorithm             the hash algorithm the backup is using
     */
    public DefaultBackupScopePartitioner(
            final int batchSize,
            @NonNull final DuplicateHandlingStrategy duplicateHandlingStrategy,
            @NonNull final HashAlgorithm hashAlgorithm) {
        this.batchSize = batchSize;
        this.groupingFunction = duplicateHandlingStrategy.fileGroupingFunctionForHash(hashAlgorithm);
    }

    @Override
    @NotNull
    public List<List<List<FileMetadata>>> partitionBackupScope(@NonNull final Collection<FileMetadata> scope) {
        final var groupedScope = filterAndGroup(scope);
        return partition(groupedScope);
    }

    @NotNull
    private Collection<List<FileMetadata>> filterAndGroup(@NotNull final Collection<FileMetadata> scope) {
        return scope.stream()
                .filter(metadata -> metadata.getStatus().isStoreContent())
                .filter(metadata -> metadata.getFileType().isContentSource())
                .collect(Collectors.groupingBy(groupingFunction))
                .values();
    }

    @NotNull
    private List<List<List<FileMetadata>>> partition(@NotNull final Collection<List<FileMetadata>> groupedScope) {
        final List<List<List<FileMetadata>>> partitionedScope = new ArrayList<>();
        var batch = new ArrayList<List<FileMetadata>>();
        var size = 0;
        for (final var group : groupedScope) {
            batch.add(group);
            size += group.size();
            if (size >= batchSize) {
                partitionedScope.add(batch);
                batch = new ArrayList<>();
                size = 0;
            }
        }
        if (!batch.isEmpty()) {
            partitionedScope.add(batch);
        }
        return partitionedScope;
    }
}
