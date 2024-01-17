package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

class FileMetadataSetterFactoryTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testNewInstanceShouldThrowExceptionWhenCalledWithNullRestoreTargets() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> FileMetadataSetterFactory.newInstance(null));

        //then + exception
    }

    @Test
    void testNewInstanceShouldCreatePosixInstanceWhenOsIsNotWindows() {
        //given
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(Path.of("a"), Path.of("b"))));

        //when
        final var actual = FileMetadataSetterFactory.newInstance(restoreTargets, false);

        //then
        Assertions.assertEquals(PosixFileMetadataSetter.class, actual.getClass());
    }

    @Test
    void testNewInstanceShouldCreateWindowsInstanceWhenOsIsWindows() {
        //given
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(Path.of("a"), Path.of("b"))));

        //when
        final var actual = FileMetadataSetterFactory.newInstance(restoreTargets, true);

        //then
        Assertions.assertEquals(WindowsFileMetadataSetter.class, actual.getClass());
    }
}
