package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryBackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFilePathSetRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.ForkJoinPool;

/**
 * The single source where all repository classes are collected.
 */
@Getter
@AllArgsConstructor
public enum DataRepositories {

    /**
     * Uses in-memory implementations for each repository.
     */
    IN_MEMORY(
            new InMemoryFilePathSetRepository(),
            new InMemoryFileMetadataSetRepository(),
            new InMemoryArchivedFileMetadataSetRepository(),
            new InMemoryBackupPathChangeStatusMapRepository());

    /**
     * Returns the default instance of the data repositories.
     *
     * @return the default instance
     */
    public static DataRepositories getDefaultInstance() {
        return IN_MEMORY;
    }

    @Getter
    private final ForkJoinPool singleThreadedPool = new ForkJoinPool(1);

    @NonNull
    private final FilePathSetRepository filePathSetRepository;

    @NonNull
    private final FileMetadataSetRepository fileMetadataSetRepository;

    @NonNull
    private final ArchivedFileMetadataSetRepository archivedFileMetadataSetRepository;

    @NonNull
    private final BackupPathChangeStatusMapRepository backupPathChangeStatusMapRepository;

}
