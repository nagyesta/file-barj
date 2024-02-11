package com.github.nagyesta.filebarj.core.config;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

class RestoreTaskTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullRestoreTargets() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> RestoreTask.builder()
                .restoreTargets(null));

        //then + exception
    }

    @Test
    void testBuilderShouldCreateInstanceWhenCalledWithValidData() {
        //given
        final var dryRun = true;
        final var threads = 2;
        final var deleteFilesNotInBackup = true;
        final var restoreTargets = new RestoreTargets(Set.of(
                new RestoreTarget(BackupPath.of(Path.of("source")), Path.of("target"))));

        //when
        final var actual = RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .dryRun(dryRun)
                .threads(threads)
                .deleteFilesNotInBackup(deleteFilesNotInBackup)
                .build();

        //then
        Assertions.assertEquals(dryRun, actual.isDryRun());
        Assertions.assertEquals(threads, actual.getThreads());
        Assertions.assertEquals(deleteFilesNotInBackup, actual.isDeleteFilesNotInBackup());
        Assertions.assertEquals(restoreTargets, actual.getRestoreTargets());
    }
}
