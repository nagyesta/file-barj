package com.github.nagyesta.filebarj.job.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Console;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CliKeyGenParserTest {

    @Test
    void testConstructorShouldThrowExceptionWhenNoArgsArePassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliKeyGenParser(new String[0], mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenUnknownOptionIsPassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliKeyGenParser(new String[]{"--unknown"}, mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenRequiredKeyStoreParametersArePassed() {
        //given
        final var store = Path.of("key-store.p12");
        final var alias = "default";
        final var password = new char[] {'a', 'b', 'c'};
        final var args = new String[] {
                "--key-store", store.toString()
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);

        //when
        final var underTest = new CliKeyGenParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(store.toAbsolutePath(), actual.getKeyStore());
        Assertions.assertEquals(alias, actual.getAlias());
        Assertions.assertArrayEquals(password, actual.getPassword());
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenAllKeyStoreParametersArePassed() {
        //given
        final var store = Path.of("key-store.p12");
        final var alias = "alias";
        final var password = new char[] {'a', 'b', 'c'};
        final var args = new String[] {
                "--key-store", store.toString(),
                "--key-alias", alias
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);

        //when
        final var underTest = new CliKeyGenParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(store.toAbsolutePath(), actual.getKeyStore());
        Assertions.assertEquals(alias, actual.getAlias());
        Assertions.assertArrayEquals(password, actual.getPassword());
    }

    @Test
    void testConstructorShouldThrowExceptionWhenPasswordsDoNotMatch() {
        //given
        final var store = Path.of("key-store.p12");
        final var alias = "alias";
        final var password1 = new char[] {'a', 'b', 'c'};
        final var password2 = new char[] {'a', 'b', 'd'};
        final var args = new String[] {
                "--key-store", store.toString(),
                "--key-alias", alias
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password1).thenReturn(password2);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliKeyGenParser(args, console));

        //then + exception
    }
}
