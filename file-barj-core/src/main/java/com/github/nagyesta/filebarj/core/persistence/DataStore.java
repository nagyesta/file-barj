package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.h2.H2ArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.h2.H2BackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.h2.H2FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.h2.H2FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryBackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFilePathSetRepository;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

/**
 * The single source where all repository classes are collected.
 *
 * @param singleThreadedPool                  the single threaded pool
 * @param fileMetadataSetRepository           the repository for file metadata sets
 * @param filePathSetRepository               the repository for file path sets
 * @param archivedFileMetadataSetRepository   the repository for archived file metadata sets
 * @param backupPathChangeStatusMapRepository the repository for backup path change status maps
 * @param closeWith                           The runnable which will close additional resources
 */
public record DataStore(
        @NonNull ForkJoinPool singleThreadedPool,
        @NonNull FilePathSetRepository filePathSetRepository,
        @NonNull FileMetadataSetRepository fileMetadataSetRepository,
        @NonNull ArchivedFileMetadataSetRepository archivedFileMetadataSetRepository,
        @NonNull BackupPathChangeStatusMapRepository backupPathChangeStatusMapRepository,
        @NonNull Runnable closeWith) implements Closeable {

    /**
     * Uses the default implementations for each repository.
     */
    @SuppressWarnings("java:S2095")
    public static DataStore newDefaultInstance() {
        return newEmbeddedSqlInstance();
    }

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
                new InMemoryBackupPathChangeStatusMapRepository(),
                () -> {
                }
        ).registeredRepositories();
    }

    /**
     * Uses embedded SQL DB implementations for each repository.
     */
    @SuppressWarnings("java:S2095")
    public static DataStore newEmbeddedSqlInstance() {
        final var tempFile = getTempFile();
        final var dataSource = getDataSource(tempFile);
        final var jdbi = Jdbi.create(dataSource);
        final var initSql = getInitSql();
        jdbi.withHandle(handle -> handle.createScript(initSql).execute());
        jdbi.installPlugin(new H2DatabasePlugin());
        jdbi.installPlugin(new SqlObjectPlugin());
        return new DataStore(
                new ForkJoinPool(1),
                new H2FilePathSetRepository(jdbi),
                new H2FileMetadataSetRepository(jdbi),
                new H2ArchivedFileMetadataSetRepository(jdbi),
                new H2BackupPathChangeStatusMapRepository(jdbi),
                () -> {
                    try {
                        dataSource.dispose();
                    } catch (final Exception e) {
                        throw new IllegalStateException("Failed to close data source.", e);
                    }
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (final IOException e) {
                        throw new IllegalStateException("Failed to delete DB temp file: " + tempFile.toAbsolutePath(), e);
                    }
                }
        ).registeredRepositories();
    }

    private static String getInitSql() {
        final String initSql;
        try {
            initSql = IOUtils.resourceToString("/db/init.sql", StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read resource.", e);
        }
        return initSql;
    }

    private static JdbcConnectionPool getDataSource(final Path tempFile) {
        try {
            final var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:file:" + tempFile.toAbsolutePath() + ";CIPHER=AES");
            ds.setUser("sa");
            ds.setPassword(UUID.randomUUID() + " " + UUID.randomUUID());
            return JdbcConnectionPool.create(ds);
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to start H2 DB", e);
        }
    }

    private static Path getTempFile() {
        try {
            final var tempFile = Files.createTempFile(UUID.randomUUID().toString(), ".h2.db");
            tempFile.toFile().deleteOnExit();
            return tempFile;
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to create temp file for DB.", e);
        }
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
        closeWith.run();
    }
}
