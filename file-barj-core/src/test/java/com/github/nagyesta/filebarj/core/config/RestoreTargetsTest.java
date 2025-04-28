package com.github.nagyesta.filebarj.core.config;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

class RestoreTargetsTest extends TempFileAwareTest {

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
    void testMapToRestorePathShouldResolvePathWhenTheFileIsDescendantOfTheTarget(
            final String relative,
            final boolean expected) {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var target = testDataRoot.resolve("target-dir" + UUID.randomUUID());
        final var underTest = new RestoreTargets(Set.of(new RestoreTarget(BackupPath.of(source), target)));
        final var sourceFile = source.resolve(relative).normalize();
        final var targetFile = target.resolve(relative).normalize();

        //when
        final var actual = underTest.mapToRestorePath(BackupPath.of(sourceFile));

        //then
        if (expected) {
            Assertions.assertEquals(targetFile, actual);
        } else {
            //if the path is not descendant of the target, the original path is returned
            Assertions.assertEquals(sourceFile, actual);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMapToRestorePathShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var target = testDataRoot.resolve("target-dir" + UUID.randomUUID());
        final var underTest = new RestoreTargets(Set.of(new RestoreTarget(BackupPath.of(source), target)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.mapToRestorePath(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new RestoreTargets(null));

        //then + exception
    }
}
