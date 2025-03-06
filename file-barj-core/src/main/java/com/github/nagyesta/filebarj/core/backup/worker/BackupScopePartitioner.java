package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Partitions the backup scope into smaller batches.
 */
public interface BackupScopePartitioner {

    /**
     * Partitions the backup scope into smaller batches.
     *
     * @param scope the backup scope
     * @return the partitioned scope
     */
    @NotNull
    List<List<FileMetadata>> partitionBackupScope(@NotNull Collection<FileMetadata> scope);
}
