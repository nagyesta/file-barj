package com.github.nagyesta.filebarj.job;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MainTest {

    @Test
    void testMainShouldNotThrowExceptionWhenUnknownTaskSelected() {
        //given
        final var args = new String[] {};
        final var underTest = spy(new Main());
        doNothing().when(underTest).exitWithError();

        //when
        Assertions.assertDoesNotThrow(() -> underTest.execute(args));

        //then
        verify(underTest).exitWithError();
    }
}
