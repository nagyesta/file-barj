package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;

import static com.github.nagyesta.filebarj.core.restore.worker.PosixFileMetadataSetter.FULL_ACCESS;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

class FileMetadataSetterLocalTest extends TempFileAwareTest {

    private static final Path TEST_FILE_PATH = new File(
            Objects.requireNonNull(TempFileAwareTest.class.getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile())
            .toPath().toAbsolutePath();
    private static final BackupJobConfiguration BACKUP_JOB_CONFIGURATION = BackupJobConfiguration.builder()
            .backupType(BackupType.FULL)
            .sources(Set.of())
            .destinationDirectory(Path.of("destination"))
            .fileNamePrefix("prefix")
            .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
            .hashAlgorithm(HashAlgorithm.SHA256)
            .compression(CompressionAlgorithm.NONE)
            .build();

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetMetadataShouldSetPermissionsOwnerGroupAndTimestampsWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        Files.setPosixFilePermissions(expectedPath.toOsPath(), PosixFilePermissions.fromString(FULL_ACCESS));
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, null);

        //when
        underTest.setMetadata(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(metadata.getPosixPermissions(), actualMetadata.getPosixPermissions());
        Assertions.assertEquals(metadata.getOwner(), actualMetadata.getOwner());
        Assertions.assertEquals(metadata.getGroup(), actualMetadata.getGroup());
        Assertions.assertEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(metadata.getLastAccessedUtcEpochSeconds(), actualMetadata.getLastAccessedUtcEpochSeconds());
        //created time is not always set even on Unix
        Assertions.assertEquals(metadata.getHidden(), actualMetadata.getHidden());
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetMetadataShouldNotSetPermissionsOwnerGroupButSetTimestampsWhenCalledWithExistingFileAndIgnoreStrategy() throws IOException {
        //given
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        Files.setPosixFilePermissions(expectedPath.toOsPath(), PosixFilePermissions.fromString(FULL_ACCESS));
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, PermissionComparisonStrategy.IGNORE);

        //when
        underTest.setMetadata(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertNotEquals(metadata.getPosixPermissions(), actualMetadata.getPosixPermissions());
        Assertions.assertEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(metadata.getLastAccessedUtcEpochSeconds(), actualMetadata.getLastAccessedUtcEpochSeconds());
        //created time is not always set even on Unix
        Assertions.assertEquals(metadata.getHidden(), actualMetadata.getHidden());
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetMetadataShouldThrowExceptionWhenCalledWithMissingFile() {
        //given
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalStateException.class, () -> underTest.setMetadata(metadata));

        //then + exception
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetInitialPermissionsShouldSetPermissionsToAllowAllWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, null);

        //when
        underTest.setInitialPermissions(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(FULL_ACCESS, actualMetadata.getPosixPermissions());
        Assertions.assertNotEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        //created time is not always set even on Unix
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetPermissionsShouldSetPermissionsOnlyWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, null);
        Files.setPosixFilePermissions(expectedPath.toOsPath(), PosixFilePermissions.fromString(FULL_ACCESS));
        final var original = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);

        //when
        underTest.setPermissions(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(FULL_ACCESS, original.getPosixPermissions());
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(metadata.getPosixPermissions(), actualMetadata.getPosixPermissions());
        Assertions.assertNotEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        //created time is not always set even on Unix
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetOwnerAndGroupShouldNotThrowExceptionWhenUserIsNotRoot() throws IOException {
        //given
        final var sourcePath = createFileForExpectedPath(BackupPath.of(Path.of("source.png")));
        Files.setPosixFilePermissions(sourcePath.toOsPath(), PosixFilePermissions.fromString(FULL_ACCESS));
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(sourcePath.toFile(), BACKUP_JOB_CONFIGURATION);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, null);
        createFileForExpectedPath(sourcePath);
        //make sure we will be able to delete the file later
        underTest.setInitialPermissions(metadata);

        //when
        Assertions.assertDoesNotThrow(() -> underTest.setOwnerAndGroup(metadata));

        //then no exception
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetHiddenStatusShouldNotThrowExceptionWhenCalledWithExistingFile() throws IOException {
        //given
        final var sourcePath = createFileForExpectedPath(BackupPath.of(Path.of(".source.png")));
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(sourcePath.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, null);

        //when
        Assertions.assertDoesNotThrow(() -> underTest.setHiddenStatus(metadata));

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(metadata.getHidden(), actualMetadata.getHidden());
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testSetTimestampsShouldSetTimestampsWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = FileMetadataParserFactory.newInstance();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var expectedPath = createFileForExpectedPath(path);
        final var underTest = FileMetadataSetterFactory.newInstance(restoreTargets, null);
        final var original = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);

        //when
        underTest.setTimestamps(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertNotEquals(metadata.getLastModifiedUtcEpochSeconds(), original.getLastModifiedUtcEpochSeconds());
        Assertions.assertNotEquals(metadata.getCreatedUtcEpochSeconds(), original.getCreatedUtcEpochSeconds());
        Assertions.assertEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(metadata.getLastAccessedUtcEpochSeconds(), actualMetadata.getLastAccessedUtcEpochSeconds());
        //created time is not always set even on Unix
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetMetadataShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setMetadata(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetPermissionsShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setPermissions(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetInitialPermissionsShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setInitialPermissions(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetTimestampsShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setTimestamps(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testSetOwnerAndGroupShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setOwnerAndGroup(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetHiddenStatusShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = FileMetadataSetterFactory.newInstance(getRestoreTargets(testDataRoot), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setHiddenStatus(null));

        //then + exception
    }

    private RestoreTargets getRestoreTargets(final Path restoreRoot) {
        return new RestoreTargets(Set.of(new RestoreTarget(BackupPath.of(restoreRoot.getRoot().toAbsolutePath()), restoreRoot)));
    }

    private BackupPath createFileForExpectedPath(final BackupPath path) throws IOException {
        final var expectedPath = Path.of(testDataRoot.toAbsolutePath().toString(), path.toString());
        Files.createDirectories(expectedPath.getParent());
        Files.createFile(expectedPath);
        return BackupPath.of(expectedPath);
    }
}
