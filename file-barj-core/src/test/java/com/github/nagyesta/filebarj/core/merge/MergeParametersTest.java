package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MergeParametersTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given
        final var builder = MergeParameters.builder();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.backupDirectory(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given
        final var builder = MergeParameters.builder();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.fileNamePrefix(null));

        //then + exception
    }

    @Test
    void testAssertValidShouldThrowExceptionWhenCalledWithEndTimeLaterThanStartTime() {
        //given
        final var underTest = MergeParameters.builder()
                .backupDirectory(testDataRoot)
                .fileNamePrefix("prefix")
                .rangeStartEpochSeconds(0L)
                .rangeEndEpochSeconds(-1L)
                .build();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, underTest::assertValid);

        //then + exception
    }

    @Test
    void testAssertValidShouldThrowExceptionWhenCalledWithStartTimeEqualToEndTime() {
        //given
        final var underTest = MergeParameters.builder()
                .backupDirectory(testDataRoot)
                .fileNamePrefix("prefix")
                .rangeStartEpochSeconds(0L)
                .rangeEndEpochSeconds(0L)
                .build();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, underTest::assertValid);

        //then + exception
    }
}
