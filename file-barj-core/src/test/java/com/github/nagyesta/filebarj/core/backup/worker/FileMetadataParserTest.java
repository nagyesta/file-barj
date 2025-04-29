package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.util.OsUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

class FileMetadataParserTest extends TempFileAwareTest {

    public static Stream<Arguments> permissionSource() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(true, true, true, "rwxrwx---"))
                .add(Arguments.of(true, false, true, "r-xr-x---"))
                .add(Arguments.of(true, true, false, "rw-rw----"))
                .add(Arguments.of(true, false, false, "r--r-----"))
                .add(Arguments.of(false, true, false, "-w--w----"))
                .add(Arguments.of(false, false, true, "--x--x---"))
                .add(Arguments.of(false, false, false, "---------"))
                .build();
    }

    @Test
    void testNewInstanceShouldReturnPosixVariantWhenTheOsIsNotWindows() {
        //given

        //when
        final var actual = FileMetadataParserFactory.newInstance(false);

        //then
        Assertions.assertEquals(PosixFileMetadataParser.class, actual.getClass());
    }

    @Test
    void testNewInstanceShouldReturnWindowsVariantWhenTheOsIsWindows() {
        //given

        //when
        final var actual = FileMetadataParserFactory.newInstance(true);

        //then
        Assertions.assertEquals(WindowsFileMetadataParser.class, actual.getClass());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testParseShouldThrowExceptionWhenTheFileIsNull() {
        //given
        final var underTest = FileMetadataParserFactory.newInstance();
        final var configuration = getConfiguration();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.parse(null, configuration));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testParseShouldThrowExceptionWhenTheConfigurationIsNull() {
        //given
        final var underTest = FileMetadataParserFactory.newInstance();
        final var file = testDataRoot.toFile();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.parse(file, null));

        //then + exception
    }

    @Test
    void testParseShouldReturnDefaultEntityWhenTheFileDoesNotExist() {
        //given
        final var underTest = FileMetadataParserFactory.newInstance();
        final var unknownFile = new File("unknown");

        //when
        final var actual = underTest.parse(unknownFile, getConfiguration());

        //then
        Assertions.assertEquals(BackupPath.of(unknownFile.toPath().toAbsolutePath()), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.MISSING, actual.getFileType());
        Assertions.assertEquals(Change.DELETED, actual.getStatus());
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testParseShouldParseFileInformationWhenTheRegularFileExistAndAccessible() throws Exception {
        //given
        final var underTest = FileMetadataParserFactory.newInstance();
        final var tempFile = File.createTempFile(".file-barj-test-file-", ".txt", testDataRoot.toFile());
        final var tempFilePath = tempFile.toPath();
        final var content = "test";
        Files.writeString(tempFilePath, content);
        tempFile.deleteOnExit();
        setFileHidden(tempFilePath);
        final var digest = DigestUtils.sha256Hex(content);

        //when
        final var actual = underTest.parse(tempFile, getConfiguration());

        //then
        Assertions.assertEquals(BackupPath.of(tempFilePath.toAbsolutePath()), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.REGULAR_FILE, actual.getFileType());
        Assertions.assertTrue(actual.getHidden());
        final var permissionString = PosixFilePermissions.toString(Files.getPosixFilePermissions(tempFilePath));
        Assertions.assertEquals(permissionString, actual.getPosixPermissions());
        Assertions.assertEquals(Files.getOwner(tempFilePath).getName(), actual.getOwner());
        Assertions.assertNotNull(actual.getGroup());
        Assertions.assertEquals(Files.size(tempFilePath), actual.getOriginalSizeBytes());
        Assertions.assertEquals(Files.getLastModifiedTime(tempFilePath).to(TimeUnit.SECONDS), actual.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(digest, actual.getOriginalHash());
        Assertions.assertDoesNotThrow(tempFile::delete);
    }

    private BackupJobConfiguration getConfiguration() {
        return BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.NONE)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(testDataRoot.toAbsolutePath())
                .fileNamePrefix("prefix-")
                .sources(Set.of())
                .build();
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testParseShouldParseFileInformationWhenTheSymbolicLinkExistAndAccessible() throws Exception {
        //given
        final var underTest = FileMetadataParserFactory.newInstance();
        final var tempFile = File.createTempFile(".file-barj-test-file-", ".txt", testDataRoot.toFile());
        final var linkPath = Files.createSymbolicLink(
                Path.of(testDataRoot.toAbsolutePath().toString(), "file-barj-link-" + UUID.randomUUID()),
                tempFile.toPath());
        linkPath.toFile().deleteOnExit();
        final var linkTarget = tempFile.toPath().toAbsolutePath().toString();
        final var content = linkTarget.getBytes(StandardCharsets.UTF_8);
        Files.write(linkPath, content);
        tempFile.deleteOnExit();
        final var digest = DigestUtils.sha256Hex(content);

        //when
        final var actual = underTest.parse(linkPath.toFile(), getConfiguration());

        //then
        Assertions.assertEquals(BackupPath.of(linkPath.toAbsolutePath()), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.SYMBOLIC_LINK, actual.getFileType());
        Assertions.assertFalse(actual.getHidden());
        final var permissionString = PosixFilePermissions.toString(Files.getPosixFilePermissions(linkPath, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertEquals(permissionString, actual.getPosixPermissions());
        Assertions.assertEquals(Files.getOwner(linkPath).getName(), actual.getOwner());
        Assertions.assertNotNull(actual.getGroup());
        final var expectedModifiedTime = Files.getLastModifiedTime(linkPath, LinkOption.NOFOLLOW_LINKS).to(TimeUnit.SECONDS);
        Assertions.assertEquals(expectedModifiedTime, actual.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(digest, actual.getOriginalHash());
        Assertions.assertDoesNotThrow(tempFile::delete);
        Assertions.assertDoesNotThrow(linkPath.toFile()::delete);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testParseShouldParseFileInformationWhenTheDirectoryExistAndAccessible() throws Exception {
        //given
        final var underTest = FileMetadataParserFactory.newInstance();

        //when
        final var tempDirPath = testDataRoot.toAbsolutePath();
        final var actual = underTest.parse(testDataRoot.toFile(), BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.NONE)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(tempDirPath)
                .fileNamePrefix("prefix-")
                .sources(Set.of())
                .build());

        //then
        Assertions.assertEquals(BackupPath.of(tempDirPath.toAbsolutePath()), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.DIRECTORY, actual.getFileType());
        Assertions.assertFalse(actual.getHidden());
        final var permissionString = PosixFilePermissions.toString(Files.getPosixFilePermissions(tempDirPath));
        Assertions.assertEquals(permissionString, actual.getPosixPermissions());
        Assertions.assertEquals(Files.getOwner(tempDirPath).getName(), actual.getOwner());
        Assertions.assertNotNull(actual.getGroup());
        Assertions.assertEquals(Files.size(tempDirPath), actual.getOriginalSizeBytes());
        Assertions.assertEquals(Files.getLastModifiedTime(tempDirPath).to(TimeUnit.SECONDS), actual.getLastModifiedUtcEpochSeconds());
        Assertions.assertNull(actual.getOriginalHash());
    }

    @ParameterizedTest
    @MethodSource("permissionSource")
    void testConstructorOfPermissionsShouldGeneratePosixStyleDataWhenCalled(
            final boolean canRead,
            final boolean canWrite,
            final boolean canExecute,
            final String expected) {
        //given

        //when
        final var actual = new PosixFileMetadataParser.Permissions(canRead, canWrite, canExecute);

        //then
        Assertions.assertEquals(PosixFileMetadataParser.DEFAULT_OWNER, actual.owner());
        Assertions.assertEquals(PosixFileMetadataParser.DEFAULT_OWNER, actual.group());
        Assertions.assertEquals(expected, actual.permissions());
    }

    private void setFileHidden(final Path tempFilePath) throws IOException, InterruptedException {
        if (OsUtil.isWindows()) {
            Runtime.getRuntime().exec(new String[]{"attrib", "+H", tempFilePath.toAbsolutePath().toString()}).waitFor();
        }
    }
}
