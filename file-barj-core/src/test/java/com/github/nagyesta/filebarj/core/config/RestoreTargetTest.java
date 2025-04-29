package com.github.nagyesta.filebarj.core.config;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

class RestoreTargetTest extends TempFileAwareTest {

    public static Stream<Arguments> pathTraversalProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of("child", true))
                .add(Arguments.of("child/of/child", true))
                .add(Arguments.of("..", false))
                .add(Arguments.of("../sibling", false))
                .build();
    }

    @ParameterizedTest
    @MethodSource("pathTraversalProvider")
    void testMatchesArchivedFileShouldReturnTrueWhenTheFileIsDescendantOfTheTarget(
            final String relative,
            final boolean expected) {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var target = testDataRoot.resolve("target-dir" + UUID.randomUUID());
        final var underTest = new RestoreTarget(BackupPath.of(source), target);
        final var file = source.resolve(relative).normalize();

        //when
        final var actual = underTest.matchesArchivedFile(BackupPath.of(file));

        //then
        Assertions.assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("pathTraversalProvider")
    void testMapBackupPathToRestorePathShouldResolvePathWhenTheFileIsDescendantOfTheTarget(
            final String relative,
            final boolean expected) {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var target = testDataRoot.resolve("target-dir" + UUID.randomUUID());
        final var underTest = new RestoreTarget(BackupPath.of(source), target);
        final var sourceFile = BackupPath.of(source).resolve(relative);
        final var targetFile = target.resolve(relative).normalize();


        //when
        if (expected) {
            final var actual = underTest.mapBackupPathToRestorePath(sourceFile);
            //then
            Assertions.assertEquals(targetFile, actual);
        } else {
            //if the path is not descendant of the target, an exception should be thrown
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.mapBackupPathToRestorePath(sourceFile));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMapBackupPathToRestorePathShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var target = testDataRoot.resolve("target-dir" + UUID.randomUUID());
        final var underTest = new RestoreTarget(BackupPath.of(source), target);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.mapBackupPathToRestorePath(null));

        //then + exception
    }
}
