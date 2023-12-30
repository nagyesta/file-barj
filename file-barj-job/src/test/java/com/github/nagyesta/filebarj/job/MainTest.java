package com.github.nagyesta.filebarj.job;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void testMainShouldThrowExceptionWhenUnknownTaskSelected() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> Main.main(new String[0]));

        //then + exception
    }
}
