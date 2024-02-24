package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.RestoreTask;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class CrossPlatformRestoreIntegrationTest extends TempFileAwareTest {

    @Test
    void testRestoreShouldRestoreContentWhenRestoringABackupMadeOnWindows() throws IOException {
        //given
        final var restorePath = testDataRoot.resolve("restore");
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        Files.createDirectories(restorePath);
        prepareBackupFiles(Set.of("windows-backup-1707544070"), backupPath);
        final var restoredR = restorePath.resolve("R");
        final var restoredU = restorePath.resolve("U");
        final var r = new RestoreTarget(BackupPath.ofPathAsIs("R:/barj-test"), restoredR);
        final var u = new RestoreTarget(BackupPath.ofPathAsIs("U:/barj-test"), restoredU);
        final var task = RestoreTask.builder()
                .restoreTargets(new RestoreTargets(Set.of(r, u)))
                .dryRun(false)
                .threads(1)
                .permissionComparisonStrategy(PermissionComparisonStrategy.RELAXED)
                .build();

        //when
        new RestoreController(backupPath, "windows-backup", null)
                .execute(task);

        //then
        verifyContent(restoredR, restoredU);
    }

    @Test
    void testRestoreShouldRestoreContentWhenRestoringABackupMadeOnUnix() throws IOException {
        //given
        final var restorePath = testDataRoot.resolve("restore");
        final var backupPath = testDataRoot.resolve("backup");
        Files.createDirectories(backupPath);
        Files.createDirectories(restorePath);
        prepareBackupFiles(Set.of("ubuntu-backup-1707595719"), backupPath);
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

        //when
        new RestoreController(backupPath, "ubuntu-backup", null)
                .execute(task);

        //then
        verifyContent(restoredR, restoredU);
    }

    private void verifyContent(final Path restoredR, final Path restoredU) throws IOException {
        Assertions.assertEquals("11111111", Files.readString(restoredR.resolve("A/1.txt")));
        Assertions.assertEquals("22222222-22222222", Files.readString(restoredR.resolve("B/2.txt")));
        Assertions.assertEquals(restoredU.resolve("B/2.txt"), Files.readSymbolicLink(restoredU.resolve("A/1.txt/link-2.txt")));
        Assertions.assertEquals("22222222", Files.readString(restoredU.resolve("B/2.txt")));
    }
}
