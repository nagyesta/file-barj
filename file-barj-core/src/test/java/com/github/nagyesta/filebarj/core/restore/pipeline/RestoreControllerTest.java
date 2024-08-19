package com.github.nagyesta.filebarj.core.restore.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RestoreControllerTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> RestoreParameters.builder().backupDirectory(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> RestoreParameters.builder().fileNamePrefix(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestoreController(null));

        //then + exception
    }

}
