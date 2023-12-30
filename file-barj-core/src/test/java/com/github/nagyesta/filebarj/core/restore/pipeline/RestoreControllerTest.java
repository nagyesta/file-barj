package com.github.nagyesta.filebarj.core.restore.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class RestoreControllerTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestoreController(null, "prefix", null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestoreController(Path.of("dir"), null, null));

        //then + exception
    }

}
