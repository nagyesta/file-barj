package com.github.nagyesta.filebarj.job.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Console;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

class CliMergeParserTest {

    @Test
    void testConstructorShouldThrowExceptionWhenNoArgsArePassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliMergeParser(new String[0], mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenUnknownOptionIsPassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliMergeParser(new String[]{"--unknown"}, mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenRequiredParameterIsMissing() {
        //given
        final var args = new String[]{"--delete-obsolete", "true"};

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliMergeParser(args, mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldNotTouchTheConsoleWhenKeyStoreParametersAreNotPassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var delete = false;
        final var from = 123L;
        final var to = 234L;
        final var args = new String[]{
                "--delete-obsolete", String.valueOf(delete),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--from-epoch-seconds", String.valueOf(from),
                "--to-epoch-seconds", String.valueOf(to)
        };
        final var console = mock(Console.class);

        //when
        new CliMergeParser(args, console);

        //then
        verifyNoInteractions(console);
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenKeyStoreParametersAreNotPassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var delete = true;
        final var from = 123L;
        final var to = 234L;
        final var args = new String[]{
                "--delete-obsolete", String.valueOf(delete),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--from-epoch-seconds", String.valueOf(from),
                "--to-epoch-seconds", String.valueOf(to)
        };
        final var console = mock(Console.class);

        //when
        final var underTest = new CliMergeParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(delete, actual.isDeleteObsoleteFiles());
        Assertions.assertEquals(backup.toAbsolutePath(), actual.getBackupSource());
        Assertions.assertEquals(prefix, actual.getPrefix());
        Assertions.assertEquals(from, actual.getFromTimeEpochSeconds());
        Assertions.assertEquals(to, actual.getToTimeEpochSeconds());
        Assertions.assertNull(actual.getKeyProperties());
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenAllKeyStoreParametersArePassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var alias = "alias";
        final var password = new char[]{'a', 'b', 'c'};
        final var delete = true;
        final var from = 123L;
        final var to = 234L;
        final var args = new String[]{
                "--delete-obsolete", String.valueOf(delete),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--from-epoch-seconds", String.valueOf(from),
                "--to-epoch-seconds", String.valueOf(to),
                "--key-store", store.toString(),
                "--key-alias", alias
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);

        //when
        final var underTest = new CliMergeParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(delete, actual.isDeleteObsoleteFiles());
        Assertions.assertEquals(backup.toAbsolutePath(), actual.getBackupSource());
        Assertions.assertEquals(prefix, actual.getPrefix());
        Assertions.assertEquals(from, actual.getFromTimeEpochSeconds());
        Assertions.assertEquals(to, actual.getToTimeEpochSeconds());
        Assertions.assertNotNull(actual.getKeyProperties());
        Assertions.assertEquals(store.toAbsolutePath(), actual.getKeyProperties().getKeyStore());
        Assertions.assertEquals(alias, actual.getKeyProperties().getAlias());
        Assertions.assertArrayEquals(password, actual.getKeyProperties().getPassword());
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenRequiredKeyStoreParametersArePassed() {
        //given
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var alias = "default";
        final var password = new char[]{'a', 'b', 'c'};
        final var delete = true;
        final var from = 123L;
        final var to = 234L;
        final var args = new String[]{
                "--delete-obsolete", String.valueOf(delete),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--from-epoch-seconds", String.valueOf(from),
                "--to-epoch-seconds", String.valueOf(to),
                "--key-store", store.toString()
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);

        //when
        final var underTest = new CliMergeParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(delete, actual.isDeleteObsoleteFiles());
        Assertions.assertEquals(backup.toAbsolutePath(), actual.getBackupSource());
        Assertions.assertEquals(prefix, actual.getPrefix());
        Assertions.assertEquals(from, actual.getFromTimeEpochSeconds());
        Assertions.assertEquals(to, actual.getToTimeEpochSeconds());
        Assertions.assertNotNull(actual.getKeyProperties());
        Assertions.assertEquals(store.toAbsolutePath(), actual.getKeyProperties().getKeyStore());
        Assertions.assertEquals(alias, actual.getKeyProperties().getAlias());
        Assertions.assertArrayEquals(password, actual.getKeyProperties().getPassword());
    }

}
