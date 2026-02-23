package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryBackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFilePathSetRepository;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.util.concurrent.ForkJoinPool;

/**
 * The single source where all repository classes are collected.
 *
 * @param singleThreadedPool                  the single threaded pool
 * @param fileMetadataSetRepository           the repository for file metadata sets
 * @param filePathSetRepository               the repository for file path sets
 * @param archivedFileMetadataSetRepository   the repository for archived file metadata sets
 * @param backupPathChangeStatusMapRepository the repository for backup path change status maps
 */
public record DataStore(
        @NonNull ForkJoinPool singleThreadedPool,
        @NonNull FilePathSetRepository filePathSetRepository,
        @NonNull FileMetadataSetRepository fileMetadataSetRepository,
        @NonNull ArchivedFileMetadataSetRepository archivedFileMetadataSetRepository,
        @NonNull BackupPathChangeStatusMapRepository backupPathChangeStatusMapRepository) implements Closeable {

    /**
     * Uses in-memory implementations for each repository.
     */
    @SuppressWarnings("java:S2095")
    public static DataStore newInMemoryInstance() {
        return new DataStore(
                new ForkJoinPool(1),
                new InMemoryFilePathSetRepository(),
                new InMemoryFileMetadataSetRepository(),
                new InMemoryArchivedFileMetadataSetRepository(),
                new InMemoryBackupPathChangeStatusMapRepository()
        ).registeredRepositories();
    }

    private DataStore registeredRepositories() {
        this.filePathSetRepository.registerWith(this);
        this.fileMetadataSetRepository.registerWith(this);
        this.archivedFileMetadataSetRepository.registerWith(this);
        this.backupPathChangeStatusMapRepository.registerWith(this);
        return this;
    }

    public boolean areAllRepositoriesClosed() {
        return filePathSetRepository.isClosed()
                && fileMetadataSetRepository.isClosed()
                && backupPathChangeStatusMapRepository.isClosed()
                && archivedFileMetadataSetRepository.isClosed();
    }

    public void close() {
        singleThreadedPool.shutdownNow();
        IOUtils.closeQuietly(filePathSetRepository);
        IOUtils.closeQuietly(fileMetadataSetRepository);
        IOUtils.closeQuietly(archivedFileMetadataSetRepository);
        IOUtils.closeQuietly(backupPathChangeStatusMapRepository);
    }
}
