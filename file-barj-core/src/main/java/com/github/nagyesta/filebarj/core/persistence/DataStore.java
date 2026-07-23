package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.model.enums.OperatingSystem;
import com.github.nagyesta.filebarj.core.persistence.h2.H2ArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.h2.H2BackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.h2.H2FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.h2.H2FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryArchivedFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryBackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFilePathSetRepository;
import com.github.nagyesta.filebarj.core.util.OsUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
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
@Slf4j
public record DataStore(
        @NonNull ForkJoinPool singleThreadedPool,
        @NonNull FilePathSetRepository filePathSetRepository,
        @NonNull FileMetadataSetRepository fileMetadataSetRepository,
        @NonNull ArchivedFileMetadataSetRepository archivedFileMetadataSetRepository,
        @NonNull BackupPathChangeStatusMapRepository backupPathChangeStatusMapRepository,
        @NonNull Runnable closeWith) implements Closeable {

    private static final String H2_SUFFIX = ".mv.db";
    private static final String H2_TRACE_SUFFIX = ".trace.db";
    /**
     * The chunk size of batch DB operations.
     */
    public static final int BATCH_CHUNK_SIZE = 2500;

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
        final var reandomUuid = UUID.randomUUID();
        final var dbFile = getTempFile(reandomUuid, H2_SUFFIX);
        final var traceFile = getTempFile(reandomUuid, H2_TRACE_SUFFIX);
        final var dataSource = getDataSource(dbFile);
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
                        Files.deleteIfExists(dbFile);
                    } catch (final IOException e) {
                        throw new IllegalStateException("Failed to delete DB temp file: " + dbFile.toAbsolutePath(), e);
                    }
                    try {
                        Files.deleteIfExists(traceFile);
                    } catch (final IOException e) {
                        throw new IllegalStateException("Failed to delete DB temp file: " + traceFile.toAbsolutePath(), e);
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
            ds.setURL("jdbc:h2:file:" + removeExtension(tempFile) + ";CIPHER=AES");
            ds.setUser("sa");
            ds.setPassword(UUID.randomUUID() + " " + UUID.randomUUID());
            return JdbcConnectionPool.create(ds);
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to start H2 DB", e);
        }
    }

    private static Path getTempFile(
            final UUID randomValue,
            final String suffix) {
        try {
            final var tempDir = Path.of(System.getProperty("java.io.tmpdir"));
            final var tempPath = tempDir.resolve("file-barj-" + randomValue + suffix);
            Files.createFile(tempPath);
            final var tempFile = tempPath.toFile();
            tempFile.deleteOnExit();
            //set permissions
            final var os = OsUtil.getOs();
            if (os == OperatingSystem.LINUX || os == OperatingSystem.MAC) {
                Files.setPosixFilePermissions(tempPath, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } else {
                final var readableSet = tempFile.setReadable(true, true);
                final var writableSet = tempFile.setWritable(true, true);
                final var notExecutableSet = tempFile.setExecutable(false, true);
                if (!readableSet || !writableSet || !notExecutableSet) {
                    log.warn("Could not set H2 DB file properties for: {}", tempPath.toAbsolutePath());
                }
            }
            return tempPath;
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to create temp file for DB.", e);
        }
    }

    private static String removeExtension(final Path tempFile) {
        return tempFile.toAbsolutePath().toString().replace(H2_SUFFIX, "");
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
