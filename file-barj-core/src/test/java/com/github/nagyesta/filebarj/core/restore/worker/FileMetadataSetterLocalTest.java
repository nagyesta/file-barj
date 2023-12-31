package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserLocal;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;

class FileMetadataSetterLocalTest extends TempFileAwareTest {

    private static final Path TEST_FILE_PATH = Path.of(
            Objects.requireNonNull(TempFileAwareTest.class.getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile());
    public static final BackupJobConfiguration BACKUP_JOB_CONFIGURATION = BackupJobConfiguration.builder()
            .backupType(BackupType.FULL)
            .sources(Set.of())
            .destinationDirectory(Path.of("destination"))
            .fileNamePrefix("prefix")
            .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
            .hashAlgorithm(HashAlgorithm.SHA256)
            .compression(CompressionAlgorithm.NONE)
            .build();

    @Test
    void testSetMetadataShouldSetPermissionsOwnerGroupAndTimestampsWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(restoreTargets);

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
        Assertions.assertEquals(metadata.getCreatedUtcEpochSeconds(), actualMetadata.getCreatedUtcEpochSeconds());
        Assertions.assertEquals(metadata.getHidden(), actualMetadata.getHidden());
    }

    @Test
    void testSetMetadataShouldThrowExceptionWhenCalledWithMissingFile() {
        //given
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalStateException.class, () -> underTest.setMetadata(metadata));

        //then + exception
    }

    @Test
    void testSetInitialPermissionsShouldSetPermissionsToAllowAllWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(restoreTargets);

        //when
        underTest.setInitialPermissions(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(FileMetadataSetterLocal.FULL_ACCESS, actualMetadata.getPosixPermissions());
        Assertions.assertNotEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        Assertions.assertNotEquals(metadata.getCreatedUtcEpochSeconds(), actualMetadata.getCreatedUtcEpochSeconds());
    }

    @Test
    void testSetPermissionsShouldSetPermissionsOnlyWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(restoreTargets);
        Files.setPosixFilePermissions(expectedPath, PosixFilePermissions.fromString(FileMetadataSetterLocal.FULL_ACCESS));
        final var original = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);

        //when
        underTest.setPermissions(metadata);

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(FileMetadataSetterLocal.FULL_ACCESS, original.getPosixPermissions());
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(metadata.getPosixPermissions(), actualMetadata.getPosixPermissions());
        Assertions.assertNotEquals(metadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds());
        Assertions.assertNotEquals(metadata.getCreatedUtcEpochSeconds(), actualMetadata.getCreatedUtcEpochSeconds());
    }

    @Test
    void testSetOwnerAndGroupShouldNotThrowExceptionWhenUserIsNotRoot() throws IOException {
        //given
        final var sourcePath = createFileForExpectedPath(Path.of("source.png"));
        Files.setPosixFilePermissions(sourcePath, PosixFilePermissions.fromString(FileMetadataSetterLocal.FULL_ACCESS));
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(sourcePath.toFile(), BACKUP_JOB_CONFIGURATION);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(restoreTargets);
        createFileForExpectedPath(sourcePath);
        //make sure we will be able to delete the file later
        underTest.setInitialPermissions(metadata);

        //when
        Assertions.assertDoesNotThrow(() -> underTest.setOwnerAndGroup(metadata));

        //then no exception
    }

    @Test
    void testSetHiddenStatusShouldNotThrowExceptionWhenCalledWithExistingFile() throws IOException {
        //given
        final var sourcePath = createFileForExpectedPath(Path.of(".source.png"));
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(sourcePath.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var expectedPath = createFileForExpectedPath(path);
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(restoreTargets);

        //when
        Assertions.assertDoesNotThrow(() -> underTest.setHiddenStatus(metadata));

        //then
        final var actualMetadata = parser.parse(expectedPath.toFile(), BACKUP_JOB_CONFIGURATION);
        Assertions.assertEquals(expectedPath, actualMetadata.getAbsolutePath());
        Assertions.assertEquals(metadata.getHidden(), actualMetadata.getHidden());
    }

    @Test
    void testSetTimestampsShouldSetTimestampsWhenCalledWithExistingFile() throws IOException {
        //given
        final var parser = new FileMetadataParserLocal();
        final var metadata = parser.parse(TEST_FILE_PATH.toFile(), BACKUP_JOB_CONFIGURATION);
        final var path = metadata.getAbsolutePath();
        final var restoreTargets = getRestoreTargets(testDataRoot);
        final var expectedPath = createFileForExpectedPath(path);
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(restoreTargets);
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
        Assertions.assertEquals(metadata.getCreatedUtcEpochSeconds(), actualMetadata.getCreatedUtcEpochSeconds());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetMetadataShouldThrowExceptionWhenCalledWithNull() {
        //given
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setMetadata(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetPermissionsShouldThrowExceptionWhenCalledWithNull() {
        //given
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setPermissions(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetInitialPermissionsShouldThrowExceptionWhenCalledWithNull() {
        //given
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setInitialPermissions(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetTimestampsShouldThrowExceptionWhenCalledWithNull() {
        //given
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setTimestamps(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetOwnerAndGroupShouldThrowExceptionWhenCalledWithNull() {
        //given
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setOwnerAndGroup(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSetHiddenStatusShouldThrowExceptionWhenCalledWithNull() {
        //given
        final FileMetadataSetter underTest = new FileMetadataSetterLocal(getRestoreTargets(testDataRoot));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.setHiddenStatus(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new FileMetadataSetterLocal(null));

        //then + exception
    }

    private RestoreTargets getRestoreTargets(final Path restoreRoot) {
        return new RestoreTargets(Set.of(new RestoreTarget(restoreRoot.getRoot().toAbsolutePath(), restoreRoot)));
    }

    private Path createFileForExpectedPath(final Path path) throws IOException {
        final var expectedPath = Path.of(testDataRoot.toAbsolutePath().toString(),
                path.subpath(0, path.getNameCount()).toString());
        Files.createDirectories(expectedPath.getParent());
        Files.createFile(expectedPath);
        return expectedPath;
    }
}
