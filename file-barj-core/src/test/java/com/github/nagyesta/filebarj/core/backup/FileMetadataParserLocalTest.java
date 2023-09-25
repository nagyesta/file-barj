package com.github.nagyesta.filebarj.core.backup;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class FileMetadataParserLocalTest {
    private static final File TEMP_DIR = new File(System.getProperty("java.io.tmpdir"));

    @Test
    void testParseShouldThrowExceptionWhenTheFileDoesNotExist() {
        //given
        final FileMetadataParserLocal underTest = new FileMetadataParserLocal();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.parse(new File("unknown"), BackupJobConfiguration.builder().build()));

        //then + exception
    }

    @Test
    void testParseShouldParseFileInformationWhenTheRegularFileExistAndAccessible() throws Exception {
        //given
        final FileMetadataParserLocal underTest = new FileMetadataParserLocal();
        final File tempFile = File.createTempFile(".file-barj-test-file-", ".txt", TEMP_DIR);
        final Path tempFilePath = tempFile.toPath();
        final String content = "test";
        Files.writeString(tempFilePath, content);
        tempFile.deleteOnExit();
        setFileHidden(tempFilePath);
        final String digest = DigestUtils.sha256Hex(content);

        //when
        final FileMetadata actual = underTest.parse(tempFile, BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(TEMP_DIR.toPath())
                .fileNamePrefix("prefix-")
                .sources(Set.of())
                .build());

        //then
        Assertions.assertEquals(tempFilePath.toAbsolutePath(), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.REGULAR_FILE, actual.getFileType());
        Assertions.assertTrue(actual.getHidden());
        final String permissionString = PosixFilePermissions.toString(Files.getPosixFilePermissions(tempFilePath));
        Assertions.assertEquals(permissionString, actual.getPosixPermissions());
        Assertions.assertEquals(Files.getOwner(tempFilePath).getName(), actual.getOwner());
        Assertions.assertNotNull(actual.getGroup());
        Assertions.assertEquals(Files.size(tempFilePath), actual.getOriginalSizeBytes());
        Assertions.assertEquals(Files.getLastModifiedTime(tempFilePath).to(TimeUnit.SECONDS), actual.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(digest, actual.getOriginalChecksum());
        Assertions.assertDoesNotThrow(tempFile::delete);
    }

    @Test
    void testParseShouldParseFileInformationWhenTheSymbolicLinkExistAndAccessible() throws Exception {
        //given
        final FileMetadataParserLocal underTest = new FileMetadataParserLocal();
        final File tempFile = File.createTempFile(".file-barj-test-file-", ".txt", TEMP_DIR);
        final Path linkPath = Files.createSymbolicLink(
                Path.of(TEMP_DIR.getAbsolutePath(), "file-barj-link-" + UUID.randomUUID()),
                tempFile.toPath());
        linkPath.toFile().deleteOnExit();
        final String content = linkPath.toAbsolutePath().toString();
        Files.writeString(linkPath, content);
        tempFile.deleteOnExit();
        final String digest = DigestUtils.sha256Hex(content);

        //when
        final FileMetadata actual = underTest.parse(linkPath.toFile(), BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(TEMP_DIR.toPath())
                .fileNamePrefix("prefix-")
                .sources(Set.of())
                .build());

        //then
        Assertions.assertEquals(linkPath.toAbsolutePath(), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.SYMBOLIC_LINK, actual.getFileType());
        Assertions.assertFalse(actual.getHidden());
        final String permissionString = PosixFilePermissions.toString(Files.getPosixFilePermissions(linkPath, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertEquals(permissionString, actual.getPosixPermissions());
        Assertions.assertEquals(Files.getOwner(linkPath).getName(), actual.getOwner());
        Assertions.assertNotNull(actual.getGroup());
        final long expectedModifiedTime = Files.getLastModifiedTime(linkPath, LinkOption.NOFOLLOW_LINKS).to(TimeUnit.SECONDS);
        Assertions.assertEquals(expectedModifiedTime, actual.getLastModifiedUtcEpochSeconds());
        Assertions.assertEquals(digest, actual.getOriginalChecksum());
        Assertions.assertDoesNotThrow(tempFile::delete);
        Assertions.assertDoesNotThrow(linkPath.toFile()::delete);
    }

    @Test
    void testParseShouldParseFileInformationWhenTheDirectoryExistAndAccessible() throws Exception {
        //given
        final FileMetadataParserLocal underTest = new FileMetadataParserLocal();

        //when
        final Path tempDirPath = TEMP_DIR.toPath();
        final FileMetadata actual = underTest.parse(TEMP_DIR, BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(tempDirPath)
                .fileNamePrefix("prefix-")
                .sources(Set.of())
                .build());

        //then
        Assertions.assertEquals(tempDirPath.toAbsolutePath(), actual.getAbsolutePath());
        Assertions.assertEquals(FileType.DIRECTORY, actual.getFileType());
        Assertions.assertFalse(actual.getHidden());
        final String permissionString = PosixFilePermissions.toString(Files.getPosixFilePermissions(tempDirPath));
        Assertions.assertEquals(permissionString, actual.getPosixPermissions());
        Assertions.assertEquals(Files.getOwner(tempDirPath).getName(), actual.getOwner());
        Assertions.assertNotNull(actual.getGroup());
        Assertions.assertEquals(Files.size(tempDirPath), actual.getOriginalSizeBytes());
        Assertions.assertEquals(Files.getLastModifiedTime(tempDirPath).to(TimeUnit.SECONDS), actual.getLastModifiedUtcEpochSeconds());
        Assertions.assertNull(actual.getOriginalChecksum());
    }

    private void setFileHidden(final Path tempFilePath) throws IOException, InterruptedException {
        if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            Runtime.getRuntime().exec("attrib +H " + tempFilePath.toAbsolutePath()).waitFor();
        }
    }
}
