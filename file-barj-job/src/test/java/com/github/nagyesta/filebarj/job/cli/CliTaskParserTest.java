package com.github.nagyesta.filebarj.job.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CliTaskParserTest {

    @Test
    void testConstructorShouldThrowExceptionWhenNoArgsArePassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliTaskParser(new String[0]));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenUnknownOptionIsPassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliTaskParser(new String[]{"--unknown"}));

        //then + exception
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenKeyGenerationIsSelected() {
        //given
        final var args = new String[]{"--gen-keys"};

        //when
        final var underTest = new CliTaskParser(args);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(Task.GEN_KEYS, actual);
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenBackupIsSelected() {
        //given
        final var args = new String[]{"--backup"};

        //when
        final var underTest = new CliTaskParser(args);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(Task.BACKUP, actual);
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenRestoreIsSelected() {
        //given
        final var args = new String[]{"--restore"};

        //when
        final var underTest = new CliTaskParser(args);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(Task.RESTORE, actual);
    }

    @Test
    void testConstructorShouldThrowExceptionWhenPartialMatchIsFound() {
        //given
        final var args = new String[]{"--res"};

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliTaskParser(args));

        //then + exception
    }
}
