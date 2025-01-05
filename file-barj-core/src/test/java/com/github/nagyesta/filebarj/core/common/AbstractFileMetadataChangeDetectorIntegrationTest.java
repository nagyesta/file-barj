package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.AppVersion;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

abstract class AbstractFileMetadataChangeDetectorIntegrationTest extends TempFileAwareTest {

    private static final int ONE_SECOND = 1000;
    static final FileMetadataParser PARSER = FileMetadataParserFactory.newInstance();
    static final BackupJobConfiguration CONFIGURATION = BackupJobConfiguration.builder()
            .fileNamePrefix("prefix")
            .sources(Set.of())
            .compression(CompressionAlgorithm.NONE)
            .hashAlgorithm(HashAlgorithm.SHA256)
            .chunkSizeMebibyte(1)
            .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
            .destinationDirectory(Path.of("/tmp"))
            .backupType(BackupType.FULL)
            .build();

    public static Stream<Arguments> commonFileContentProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        "change_permission.txt",
                        "content", FileType.REGULAR_FILE, "rwxrwxrwx",
                        false,
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        false, true, Change.METADATA_CHANGED))
                .add(Arguments.of(
                        "change_content.txt",
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        false,
                        "changed", FileType.REGULAR_FILE, "rw-rw-rw-",
                        true, true, Change.CONTENT_CHANGED))
                .add(Arguments.of(
                        "deleted.txt",
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        true,
                        null, null, null,
                        true, true, Change.DELETED))
                .add(Arguments.of(
                        "new.txt",
                        null, null, null,
                        false,
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        true, true, Change.NEW))
                .add(Arguments.of(
                        "no_change.txt",
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        false,
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        false, false, Change.NO_CHANGE))
                .add(Arguments.of(
                        "change_content.txt",
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        false,
                        "b.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true, false, Change.CONTENT_CHANGED))
                .add(Arguments.of(
                        "deleted.txt",
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true,
                        null, null, null,
                        true, true, Change.DELETED))
                .add(Arguments.of(
                        "new.txt",
                        null, null, null,
                        false,
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true, true, Change.NEW))
                .add(Arguments.of(
                        "no_change.txt",
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        false,
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        false, false, Change.NO_CHANGE))
                .add(Arguments.of(
                        "change_permission",
                        null, FileType.DIRECTORY, "rwxrwxrwx",
                        false,
                        null, FileType.DIRECTORY, "rw-rw-rw-",
                        false, true, Change.METADATA_CHANGED))
                .add(Arguments.of(
                        "deleted",
                        null, FileType.DIRECTORY, "rw-rw-rw-",
                        true,
                        null, null, null,
                        false, true, Change.DELETED))
                .add(Arguments.of(
                        "new",
                        null, null, null,
                        false,
                        null, FileType.DIRECTORY, "rw-rw-rw-",
                        false, true, Change.NEW))
                .add(Arguments.of(
                        "no_change",
                        null, FileType.DIRECTORY, "rw-rw-rw-",
                        false,
                        null, FileType.DIRECTORY, "rw-rw-rw-",
                        false, false, Change.NO_CHANGE))
                .add(Arguments.of(
                        "type_change",
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        true,
                        null, FileType.DIRECTORY, "rw-rw-rw-",
                        true, true, Change.CONTENT_CHANGED))
                .add(Arguments.of(
                        "type_change.txt",
                        "content", FileType.REGULAR_FILE, "rw-rw-rw-",
                        true,
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true, true, Change.CONTENT_CHANGED))
                .build();
    }

    protected SimpleFileMetadataChangeDetector getDefaultSimpleFileMetadataChangeDetector(final FileMetadata prev) {
        return new SimpleFileMetadataChangeDetector(getDatabaseWithSingleFile(prev, HashAlgorithm.NONE), null);
    }

    protected HashingFileMetadataChangeDetector getDefaultHashingFileMetadataChangeDetector(final FileMetadata prev) {
        return new HashingFileMetadataChangeDetector(getDatabaseWithSingleFile(prev, HashAlgorithm.SHA256), null);
    }

    protected FileMetadata createMetadata(
            final String name, final String content, final FileType type, final String permission, final boolean recreate)
            throws IOException {
        final var path = testDataRoot.resolve(name);
        final var exists = deleteOldVersionIfNecessary(path, type, recreate);
        if (type == FileType.DIRECTORY) {
            if (!exists) {
                Files.createDirectory(path);
            }
            setPermissions(permission, path);
        } else if (type == FileType.REGULAR_FILE) {
            if (!exists) {
                Files.createFile(path);
            }
            if (!content.equals(Files.readString(path))) {
                Files.writeString(path, content);
            }
            setPermissions(permission, path);
        } else if (type == FileType.SYMBOLIC_LINK) {
            final var target = testDataRoot.resolve(content);
            if (!Files.exists(target)) {
                Files.createFile(target);
            }
            if (!exists || !target.equals(Files.readSymbolicLink(path))) {
                Files.deleteIfExists(path);
                Files.createSymbolicLink(path, target);
            }
        }
        return PARSER.parse(path.toFile(), CONFIGURATION);
    }

    protected boolean deleteOldVersionIfNecessary(
            final Path path, final FileType type, final boolean recreate) {
        if (recreate) {
            FileUtils.deleteQuietly(path.toFile());
            Assertions.assertFalse(Files.exists(path));
        }
        final var exists = Files.exists(path);
        if (exists) {
            if (type == FileType.DIRECTORY) {
                Assertions.assertTrue(Files.isDirectory(path));
            } else if (type == FileType.REGULAR_FILE) {
                Assertions.assertTrue(Files.isRegularFile(path));
            } else if (type == FileType.SYMBOLIC_LINK) {
                Assertions.assertTrue(Files.isSymbolicLink(path));
            } else {
                Assertions.fail("File type is null, expecting file to be deleted.");
            }
        }
        return exists;
    }

    protected static void setPermissions(final String permission, final Path path) throws IOException {
        if (!permission.equals(PosixFilePermissions.toString(Files.getPosixFilePermissions(path)))) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permission));
        }
    }

    protected static void waitASecond() throws InterruptedException {
        Thread.sleep(ONE_SECOND);
    }

    protected static InMemoryManifestDatabase getDatabaseWithSingleFile(
            final Map<Integer, FileMetadata> fileMetadataMap, final HashAlgorithm hashAlgorithm) {
        final var database = new InMemoryManifestDatabase();
        fileMetadataMap.forEach((increment, fileMetadata) -> {
            final var manifestId = database.persistIncrement(getManifest(increment, hashAlgorithm));
            database.persistFileMetadata(manifestId, fileMetadata);
        });
        return database;
    }

    private static InMemoryManifestDatabase getDatabaseWithSingleFile(
            final FileMetadata fileMetadata, final HashAlgorithm hashAlgorithm) {
        final var database = new InMemoryManifestDatabase();
        final var manifestId = database.persistIncrement(getManifest(0, hashAlgorithm));
        database.persistFileMetadata(manifestId, fileMetadata);
        return database;
    }

    private static BackupIncrementManifest getManifest(final int version, final HashAlgorithm hashAlgorithm) {
        final var tmp = Path.of(System.getProperty("java.io.tmpdir"));
        final BackupType backupType;
        if (version == 0) {
            backupType = BackupType.FULL;
        } else {
            backupType = BackupType.INCREMENTAL;
        }
        return BackupIncrementManifest.builder()
                .startTimeUtcEpochSeconds(1L)
                .appVersion(new AppVersion(AppVersion.DEFAULT_VERSION))
                .fileNamePrefix("test-" + version + "-")
                .backupType(backupType)
                .versions(new TreeSet<>(Set.of(version)))
                .configuration(BackupJobConfiguration.builder()
                        .backupType(BackupType.FULL)
                        .chunkSizeMebibyte(1)
                        .compression(CompressionAlgorithm.NONE)
                        .destinationDirectory(tmp.resolve("backup"))
                        .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                        .sources(Set.of(BackupSource.builder()
                                .path(BackupPath.of(tmp.resolve("source")))
                                .build()))
                        .hashAlgorithm(hashAlgorithm)
                        .fileNamePrefix("test-")
                        .build())
                .files(new HashMap<>())
                .archivedEntries(new HashMap<>())
                .build();
    }
}
