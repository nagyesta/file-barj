package com.github.nagyesta.filebarj.core.inspect.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IncrementInspectionControllerTest extends TempFileAwareTest {

    private static final long ONE_SECOND = 1000L;
    private static final int BACKUP_COUNT = 3;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullBackupDirectory() {
        //given
        final var fileNamePrefix = "prefix";

        //when
        assertThrows(IllegalArgumentException.class, () -> new IncrementInspectionController(null, fileNamePrefix, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given
        final var backupDirectory = testDataRoot;

        //when
        assertThrows(IllegalArgumentException.class, () -> new IncrementInspectionController(backupDirectory, null, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testInspectContentShouldThrowExceptionWhenCalledWithNullOutputFile() throws IOException {
        //given
        final var originalDirectory = testDataRoot.resolve("original");
        final var backupDirectory = testDataRoot.resolve("backup");
        Files.createDirectories(originalDirectory);
        Files.writeString(originalDirectory.resolve("file1.txt"), "content");
        doBackup(backupDirectory, originalDirectory, "prefix");
        final var underTest = new IncrementInspectionController(backupDirectory, "prefix", null);

        //when
        assertThrows(IllegalArgumentException.class, () -> underTest.inspectContent(Long.MAX_VALUE, null));

        //then + exception
    }

    @Test
    void testInspectContentShouldWriteContentWhenCalledWithNullOutputFile() throws IOException {
        //given
        final var originalDirectory = testDataRoot.resolve("original");
        final var backupDirectory = testDataRoot.resolve("backup");
        Files.createDirectories(originalDirectory);
        final var originalFile = originalDirectory.resolve("file1.txt");
        Files.writeString(originalFile, "content");
        final var prefix = "file-prefix";
        doBackup(backupDirectory, originalDirectory, prefix);
        final var underTest = new IncrementInspectionController(backupDirectory, prefix, null);
        final var outputFile = originalDirectory.resolve("content.csv");

        //when
        underTest.inspectContent(Long.MAX_VALUE, outputFile);

        //then
        final var actualContents = Files.readAllLines(outputFile);
        Assertions.assertTrue(actualContents.get(0).contains("hash_sha256"));
        Assertions.assertTrue(actualContents.get(1)
                .endsWith(FilenameUtils.separatorsToUnix(originalDirectory.toAbsolutePath().toString())));
        Assertions.assertTrue(actualContents.get(2)
                .endsWith(FilenameUtils.separatorsToUnix(originalFile.toAbsolutePath().toString())));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testInspectIncrementsShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var originalDirectory = testDataRoot.resolve("original");
        final var backupDirectory = testDataRoot.resolve("backup");
        Files.createDirectories(originalDirectory);
        Files.writeString(originalDirectory.resolve("file1.txt"), "content");
        doBackup(backupDirectory, originalDirectory, "prefix");
        final var underTest = new IncrementInspectionController(backupDirectory, "prefix", null);

        //when
        assertThrows(IllegalArgumentException.class, () -> underTest.inspectIncrements(null));

        //then + exception
    }

    @Test
    void testInspectIncrementsShouldReturnSummariesWhenCalledWithStream() throws IOException, InterruptedException {
        //given
        final var originalDirectory = testDataRoot.resolve("original");
        final var backupDirectory = testDataRoot.resolve("backup");
        Files.createDirectories(originalDirectory);
        Files.writeString(originalDirectory.resolve("file1.txt"), "content");
        final var prefix = "prefix";
        for (var i = 0; i < BACKUP_COUNT; i++) {
            doBackup(backupDirectory, originalDirectory, prefix);
            Thread.sleep(ONE_SECOND);
        }
        final var underTest = new IncrementInspectionController(backupDirectory, prefix, null);

        final var byteArrayOutputStream = new ByteArrayOutputStream();
        final var printStream = new PrintStream(byteArrayOutputStream);

        //when
        underTest.inspectIncrements(printStream);

        //then
        final var actualContents = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        final var actualCount = actualContents.lines()
                .filter(line -> line.startsWith("FULL"))
                .count();
        Assertions.assertEquals(BACKUP_COUNT, actualCount);
    }

    private static void doBackup(
            final Path backupDirectory,
            final Path originalDirectory,
            final String prefix) {
        final var configuration = BackupJobConfiguration.builder()
                .destinationDirectory(backupDirectory)
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(originalDirectory)).build()))
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .fileNamePrefix(prefix)
                .compression(CompressionAlgorithm.NONE)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP)
                .build();
        new BackupController(configuration, false).execute(1);
    }
}
