package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.RestoreTask;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.restore.pipeline.RestoreController;
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
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class MergeControllerIntegrationTest extends TempFileAwareTest {

    private static final String DASH = "-";
    private static final PrivateKey KEK = getKek();
    private static final long B_FIRST_FULL = 1707595719L;
    private static final long B_INCREMENT_1 = 1708783849L;
    private static final long B_INCREMENT_2 = 1708783920L;
    private static final long B_INCREMENT_3 = 1708783987L;
    private static final long B_SECOND_FULL = 1708856935L;
    private static final long E_FIRST_FULL = 1708883558L;
    private static final long E_INCREMENT_1 = 1708883624L;
    private static final long E_INCREMENT_2 = 1708883649L;
    private static final long E_INCREMENT_3 = 1708883671L;
    private static final long E_SECOND_FULL = 1708883739L;
    private static final String UBUNTU_BACKUP = "ubuntu-backup";
    private static final String UB_FIRST_FULL = UBUNTU_BACKUP + DASH + B_FIRST_FULL;
    private static final String UB_INCREMENT_1 = UBUNTU_BACKUP + DASH + B_INCREMENT_1;
    private static final String UB_INCREMENT_2 = UBUNTU_BACKUP + DASH + B_INCREMENT_2;
    private static final String UB_INCREMENT_3 = UBUNTU_BACKUP + DASH + B_INCREMENT_3;
    private static final String UB_SECOND_FULL = UBUNTU_BACKUP + DASH + B_SECOND_FULL;
    private static final String UBUNTU_ENCRYPTED = "ubuntu-encrypted";
    private static final String UE_FIRST_FULL = UBUNTU_ENCRYPTED + DASH + E_FIRST_FULL;
    private static final String UE_INCREMENT_1 = UBUNTU_ENCRYPTED + DASH + E_INCREMENT_1;
    private static final String UE_INCREMENT_2 = UBUNTU_ENCRYPTED + DASH + E_INCREMENT_2;
    private static final String UE_INCREMENT_3 = UBUNTU_ENCRYPTED + DASH + E_INCREMENT_3;
    private static final String UE_SECOND_FULL = UBUNTU_ENCRYPTED + DASH + E_SECOND_FULL;
    private static final Set<String> BOTH_SETS_UBUNTU_BACKUP = Set.of(
            UB_FIRST_FULL,
            UB_INCREMENT_1,
            UB_INCREMENT_2,
            UB_INCREMENT_3,
            UB_SECOND_FULL);
    private static final Set<String> BOTH_SETS_UBUNTU_ENCRYPTED = Set.of(
            UE_FIRST_FULL,
            UE_INCREMENT_1,
            UE_INCREMENT_2,
            UE_INCREMENT_3,
            UE_SECOND_FULL);
    private static final Set<String> FIRST_SET_UBUNTU_BACKUP = Set.of(
            UB_FIRST_FULL,
            UB_INCREMENT_1,
            UB_INCREMENT_2,
            UB_INCREMENT_3);
    private static final Set<String> FIRST_SET_UBUNTU_ENCRYPTED = Set.of(
            UE_FIRST_FULL,
            UE_INCREMENT_1,
            UE_INCREMENT_2,
            UE_INCREMENT_3);

    public Stream<Arguments> validRangeProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(B_FIRST_FULL, B_INCREMENT_3))
                .add(Arguments.of(B_FIRST_FULL, B_INCREMENT_2))
                .add(Arguments.of(B_FIRST_FULL, B_INCREMENT_1))
                .add(Arguments.of(B_INCREMENT_1, B_INCREMENT_2))
                .add(Arguments.of(B_INCREMENT_1, B_INCREMENT_3))
                .add(Arguments.of(B_INCREMENT_2, B_INCREMENT_3))
                .build();
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithInvalidStartTime() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_BACKUP, backupPath);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MergeController(backupPath, UBUNTU_BACKUP, null, 0L, B_INCREMENT_1));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithInvalidEndTime() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_BACKUP, backupPath);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MergeController(backupPath, UBUNTU_BACKUP, null, B_FIRST_FULL, B_INCREMENT_1 + 1L));

        //then + exception
    }

    @ParameterizedTest
    @MethodSource("validRangeProvider")
    void testConstructorShouldNotThrowExceptionWhenCalledWithValidRange(final long start, final long end) throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_BACKUP, backupPath);

        //when
        final var actual = new MergeController(backupPath, UBUNTU_BACKUP, null, start, end);

        //then
        Assertions.assertNotNull(actual);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithFullBackupAndFirstIncrementWithoutEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(BOTH_SETS_UBUNTU_BACKUP, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_BACKUP, null, B_FIRST_FULL, B_INCREMENT_1);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UB_FIRST_FULL, UB_INCREMENT_1));
        verifyBackupFilesExist(Set.of(UB_FIRST_FULL + DASH + B_INCREMENT_1, UB_INCREMENT_2, UB_INCREMENT_3, UB_SECOND_FULL));
        restoreBackups(backupPath, actual.getFileNamePrefix(), null, Map.of(
                "A/1.txt", "11111111",
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", "11111111",
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithFullBackupAndFirstIncrementWithEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(BOTH_SETS_UBUNTU_ENCRYPTED, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_ENCRYPTED, KEK, E_FIRST_FULL, E_INCREMENT_1);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UE_FIRST_FULL, UE_INCREMENT_1));
        verifyBackupFilesExist(Set.of(UE_FIRST_FULL + DASH + E_INCREMENT_1, UE_INCREMENT_2, UE_INCREMENT_3, UE_SECOND_FULL));
        restoreBackups(backupPath, actual.getFileNamePrefix(), KEK, Map.of(
                "A/1.txt", "11111111",
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", "11111111",
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithAllIncrementsWithoutEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_BACKUP, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_BACKUP, null, B_INCREMENT_1, B_INCREMENT_3);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UB_INCREMENT_1, UB_INCREMENT_2, UB_INCREMENT_3));
        verifyBackupFilesExist(Set.of(UB_FIRST_FULL, UB_INCREMENT_1 + DASH + B_INCREMENT_3));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), null, Map.of(
                "A/1.txt", DASH,
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", DASH,
                "A/2.txt", DASH,
                "A/3.txt", DASH,
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithAllIncrementsWithEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_ENCRYPTED, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_ENCRYPTED, KEK, E_INCREMENT_1, E_INCREMENT_3);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UE_INCREMENT_1, UE_INCREMENT_2, UE_INCREMENT_3));
        verifyBackupFilesExist(Set.of(UE_FIRST_FULL, UE_INCREMENT_1 + DASH + E_INCREMENT_3));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), KEK, Map.of(
                "A/1.txt", DASH,
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", DASH,
                "A/2.txt", DASH,
                "A/3.txt", DASH,
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithFirstTwoIncrementsWithoutEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_BACKUP, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_BACKUP, null, B_INCREMENT_1, B_INCREMENT_2);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UB_INCREMENT_1, UB_INCREMENT_2));
        verifyBackupFilesExist(Set.of(UB_FIRST_FULL, UB_INCREMENT_1 + DASH + B_INCREMENT_2, UB_INCREMENT_3));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), null, Map.of(
                "A/1.txt", DASH,
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", DASH,
                "A/2.txt", DASH,
                "A/3.txt", DASH,
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithFirstTwoIncrementsWithEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_ENCRYPTED, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_ENCRYPTED, KEK, E_INCREMENT_1, E_INCREMENT_2);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UE_INCREMENT_1, UE_INCREMENT_2));
        verifyBackupFilesExist(Set.of(UE_FIRST_FULL, UE_INCREMENT_1 + DASH + E_INCREMENT_2, UE_INCREMENT_3));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), KEK, Map.of(
                "A/1.txt", DASH,
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", DASH,
                "A/2.txt", DASH,
                "A/3.txt", DASH,
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithFullBackupAndTwoIncrementsWithoutEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        final var prefixes = Set.of(
                UB_FIRST_FULL,
                UB_INCREMENT_1,
                UB_INCREMENT_2);
        prepareBackupFiles(prefixes, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_BACKUP, null, B_FIRST_FULL, B_INCREMENT_2);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UB_FIRST_FULL, UB_INCREMENT_1, UB_INCREMENT_2));
        verifyBackupFilesExist(Set.of(UB_FIRST_FULL + DASH + B_INCREMENT_2));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), null, Map.of(
                "A/1.txt", "11111111",
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", "11111111",
                "A/2.txt", "2222\n",
                "A/3.txt", "333333333\n",
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldMergeSelectedRangeWhenCalledWithFullBackupAndTwoIncrementsWithEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        final var prefixes = Set.of(
                UE_FIRST_FULL,
                UE_INCREMENT_1,
                UE_INCREMENT_2);
        prepareBackupFiles(prefixes, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_ENCRYPTED, KEK, E_FIRST_FULL, E_INCREMENT_2);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UE_FIRST_FULL, UE_INCREMENT_1, UE_INCREMENT_2));
        verifyBackupFilesExist(Set.of(UE_FIRST_FULL + DASH + E_INCREMENT_2));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), KEK, Map.of(
                "A/1.txt", "11111111",
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", "11111111",
                "A/2.txt", "2222\n",
                "A/3.txt", "333333333\n",
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldDeleteIncrementsFromSelectedRangeBeforeFullBackupWhenCalledWithAllIncrementsAndFullBackupWithoutEncryption()
            throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(BOTH_SETS_UBUNTU_BACKUP, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_BACKUP, null, B_INCREMENT_1, B_SECOND_FULL);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UB_INCREMENT_1, UB_INCREMENT_2, UB_INCREMENT_3, UB_SECOND_FULL));
        verifyBackupFilesExist(Set.of(UB_FIRST_FULL, UB_SECOND_FULL + DASH + B_SECOND_FULL));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), null, Map.of(
                "A/1.txt", DASH,
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", DASH,
                "A/2.txt", DASH,
                "A/3.txt", DASH,
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecuteShouldDeleteIncrementsFromSelectedRangeBeforeFullBackupWhenCalledWithAllIncrementsAndFullBackupWithEncryption()
            throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(BOTH_SETS_UBUNTU_ENCRYPTED, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_ENCRYPTED, KEK, E_INCREMENT_1, E_SECOND_FULL);

        //when
        final var actual = underTest.execute(true);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesAreDeleted(Set.of(UE_INCREMENT_1, UE_INCREMENT_2, UE_INCREMENT_3, UE_SECOND_FULL));
        verifyBackupFilesExist(Set.of(UE_FIRST_FULL, UE_SECOND_FULL + DASH + E_SECOND_FULL));
        restoreBackups(backupPath, actual.getConfiguration().getFileNamePrefix(), KEK, Map.of(
                "A/1.txt", DASH,
                "B/2.txt", "22222222-22222222"
        ), Map.of(
                "A/1.txt", DASH,
                "A/2.txt", DASH,
                "A/3.txt", DASH,
                "B/2.txt", "22222222"
        ));
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Skipped because increment 2 content becomes corrupt during CRLF conversion")
    void testExecuteShouldNotDeleteFilesWhenCalledWithFalseFlagWithoutEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_BACKUP, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_BACKUP, null, B_INCREMENT_1, B_INCREMENT_2);

        //when
        final var actual = underTest.execute(false);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesExist(Set.of(UB_FIRST_FULL, UB_INCREMENT_1, UB_INCREMENT_2, UB_INCREMENT_3,
                UB_INCREMENT_1 + DASH + B_INCREMENT_2));
    }

    @Test
    void testExecuteShouldNotDeleteFilesWhenCalledWithFalseFlagWithEncryption() throws IOException {
        //given
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        prepareBackupFiles(FIRST_SET_UBUNTU_ENCRYPTED, backupPath);
        final var underTest = new MergeController(backupPath, UBUNTU_ENCRYPTED, KEK, E_INCREMENT_1, E_INCREMENT_2);

        //when
        final var actual = underTest.execute(false);

        //then
        Assertions.assertNotNull(actual);
        verifyBackupFilesExist(Set.of(UE_FIRST_FULL, UE_INCREMENT_1, UE_INCREMENT_2, UE_INCREMENT_3,
                UE_INCREMENT_1 + DASH + E_INCREMENT_2));
    }

    private static PrivateKey getKek() {
        try {
            final var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(MergeControllerIntegrationTest.class.getResourceAsStream("/backups/test.p12.key"), "123".toCharArray());
            return (PrivateKey) keyStore.getKey("default", "123".toCharArray());
        } catch (final Exception e) {
            Assertions.fail("Could not load kek", e);
            return null;
        }
    }

    private void restoreBackups(
            final Path backupPath,
            final String fileNamePrefix,
            final PrivateKey kek,
            final Map<String, String> rContents,
            final Map<String, String> uContents) throws IOException {
        final var restorePath = testDataRoot.resolve("restore");
        Files.createDirectories(restorePath);
        final var restoredR = restorePath.resolve("R");
        final var restoredU = restorePath.resolve("U");
        final var r = new RestoreTarget(BackupPath.ofPathAsIs("/tmp/R/barj-test"), restoredR);
        final var u = new RestoreTarget(BackupPath.ofPathAsIs("/tmp/U/barj-test"), restoredU);
        final var task = RestoreTask.builder()
                .restoreTargets(new RestoreTargets(Set.of(r, u)))
                .dryRun(false)
                .threads(1)
                .permissionComparisonStrategy(PermissionComparisonStrategy.RELAXED)
                .build();
        new RestoreController(backupPath, fileNamePrefix, kek)
                .execute(task);
        verifyContents(restoredR, rContents);
        verifyContents(restoredU, uContents);
    }

    private static void verifyContents(final Path root, final Map<String, String> expected) throws IOException {
        for (final var entry : expected.entrySet()) {
            if (entry.getValue().equals(DASH)) {
                Assertions.assertFalse(Files.exists(root.resolve(entry.getKey())),
                        "File " + root.resolve(entry.getKey()) + " should not exist.");
            } else {
                Assertions.assertEquals(entry.getValue(), Files.readString(root.resolve(entry.getKey())));
            }
        }
    }

    private void verifyBackupFilesAreDeleted(final Set<String> prefixes) {
        for (final var prefix : prefixes) {
            for (final var fileName : Stream.of(".00001.cargo", ".manifest.cargo", ".index.cargo").map(prefix::concat).toList()) {
                final var path = testDataRoot.resolve("backup").resolve(fileName);
                Assertions.assertFalse(Files.exists(path), "File " + path + " should be deleted");
            }
        }
    }

    private void verifyBackupFilesExist(final Set<String> prefixes) {
        for (final var prefix : prefixes) {
            for (final var fileName : Stream.of(".00001.cargo", ".manifest.cargo", ".index.cargo").map(prefix::concat).toList()) {
                final var path = testDataRoot.resolve("backup").resolve(fileName);
                Assertions.assertTrue(Files.exists(path), "File " + path + " should exist.");
            }
        }
    }
}
