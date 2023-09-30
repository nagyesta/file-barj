package com.github.nagyesta.filebarj.core.backup;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArchivalExceptionTest {

    @Test
    void testConstructorShouldSetMessageAndCauseWhenCalled() {
        //given
        final var message = "message";
        final var cause = new Exception();

        //when
        final var actual = new ArchivalException(message, cause);

        //then
        Assertions.assertEquals(message, actual.getMessage());
        Assertions.assertEquals(cause, actual.getCause());
    }
}
