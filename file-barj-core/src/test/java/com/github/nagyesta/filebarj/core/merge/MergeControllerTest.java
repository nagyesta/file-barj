package com.github.nagyesta.filebarj.core.merge;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MergeControllerTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MergeController(null, "prefix", null, 0L, 1L));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MergeController(testDataRoot, null, null, 0L, 1L));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithEndTimeLaterThanStartTime() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MergeController(testDataRoot, "prefix", null, 0L, -1L));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithStartTimeEqualToEndTime() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MergeController(testDataRoot, "prefix", null, 0L, 0L));

        //then + exception
    }
}
