package com.github.nagyesta.filebarj.core.restore.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RestoreControllerTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given
        final var builder = RestoreParameters.builder();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.backupDirectory(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given
        final var builder = RestoreParameters.builder();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.fileNamePrefix(null));

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
