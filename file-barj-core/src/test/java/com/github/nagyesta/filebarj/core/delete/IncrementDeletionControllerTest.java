package com.github.nagyesta.filebarj.core.delete;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IncrementDeletionControllerTest extends TempFileAwareTest {
    private static final long ONE_SECOND = 1000L;

    @SuppressWarnings({"checkstyle:MagicNumber", "MagicNumber"})
    public Stream<Arguments> validParameterProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(1, 0, 0, 0))
                .add(Arguments.of(2, 1, 3, 1))
                .add(Arguments.of(5, 4, 12, 4))
                .add(Arguments.of(5, 0, 0, 0))
                .add(Arguments.of(5, 1, 3, 1))
                .build();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullBackupDirectory() {
        //given
        final var fileNamePrefix = "prefix";

        //when
        assertThrows(IllegalArgumentException.class, () -> new IncrementDeletionController(null, fileNamePrefix, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given
        final var backupDirectory = testDataRoot;

        //when
        assertThrows(IllegalArgumentException.class, () -> new IncrementDeletionController(backupDirectory, null, null));

        //then + exception
    }

    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest
    @MethodSource("validParameterProvider")
    void testDeleteIncrementsShouldReturnSummariesWhenCalledWithStream(
            final int backups, final int skip, final long expectedBackupFiles, final long expectedHistoryFiles)
            throws IOException, InterruptedException {
        //given
        final var originalDirectory = testDataRoot.resolve("original");
        final var backupDirectory = testDataRoot.resolve("backup");
        Files.createDirectories(originalDirectory);
        Files.writeString(originalDirectory.resolve("file1.txt"), "content");
        final var prefix = "prefix";
        for (var i = 0; i < backups; i++) {
            doBackup(backupDirectory, originalDirectory, prefix);
            Thread.sleep(ONE_SECOND);
        }
        final var underTest = new IncrementDeletionController(backupDirectory, prefix, null);
        final var firstBackupStarted = Arrays.stream(Objects.requireNonNull(backupDirectory.toFile().list()))
                .filter(child -> child.startsWith(prefix) && child.endsWith(".manifest.cargo"))
                .sorted()
                .skip(skip)
                .findFirst()
                .map(child -> child.replaceFirst(Pattern.quote(prefix + "-"), "").replaceFirst("\\..+$", ""))
                .map(Long::parseLong)
                .orElseThrow();

        //when
        underTest.deleteIncrementsUntilNextFullBackupAfter(firstBackupStarted);
        Thread.sleep(ONE_SECOND);

        //then
        final var matchingBackupFiles = Arrays.stream(Objects.requireNonNull(backupDirectory.toFile().list()))
                .filter(child -> child.startsWith(prefix))
                .count();
        final var matchingHistoryFiles = Arrays.stream(Objects.requireNonNull(backupDirectory.resolve(".history").toFile().list()))
                .filter(child -> child.startsWith(prefix))
                .count();
        Assertions.assertEquals(expectedHistoryFiles, matchingHistoryFiles);
        Assertions.assertEquals(expectedBackupFiles, matchingBackupFiles);
    }

    @SuppressWarnings("SameParameterValue")
    private static void doBackup(
            final Path backupDirectory,
            final Path originalDirectory,
            final String prefix) {
        final var configuration = BackupJobConfiguration.builder()
                .destinationDirectory(backupDirectory)
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(originalDirectory)).build()))
                .backupType(BackupType.INCREMENTAL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .fileNamePrefix(prefix)
                .compression(CompressionAlgorithm.NONE)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP)
                .build();
        new BackupController(configuration, false).execute(1);
    }
}
