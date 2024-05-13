package com.github.nagyesta.filebarj.job;

import org.junit.jupiter.api.Test;

import java.io.Console;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ControllerTest {

    @Test
    void testKeygenShouldBeCalledWhenAllKeyStoreParametersArePassed() throws Exception {
        //given
        final var store = Path.of("key-store.p12");
        final var alias = "alias";
        final var password = new char[]{'a', 'b', 'c'};
        final var args = new String[]{
                "--gen-keys",
                "--key-store", store.toString(),
                "--key-alias", alias
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);
        final var underTest = spy(new Controller(args, console));
        doNothing().when(underTest).doGenerateKey(any());

        //when
        underTest.run();

        //then
        verify(underTest).doGenerateKey(any());
    }

    @Test
    void testRestoreShouldBeCalledWhenRequiredRestoreParametersArePassed() throws Exception {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var password = new char[]{'a', 'b', 'c'};
        final var args = new String[]{
                "--restore",
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--key-store", store.toString()
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);
        final var underTest = spy(new Controller(args, console));
        doNothing().when(underTest).doRestore(any());

        //when
        underTest.run();

        //then
        verify(underTest).doRestore(any());
    }

    @Test
    void testBackupShouldBeCalledWhenRequiredBackupParametersArePassed() throws Exception {
        //given
        final var config = Path.of("config.json");
        final var args = new String[]{
                "--backup",
                "--config", config.toString()
        };
        final var console = mock(Console.class);
        final var underTest = spy(new Controller(args, console));
        doNothing().when(underTest).doBackup(any());

        //when
        underTest.run();

        //then
        verify(underTest).doBackup(any());
    }

    @Test
    void testInspectContentShouldBeCalledWhenRequiredRestoreParametersArePassed() throws Exception {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var password = new char[]{'a', 'b', 'c'};
        final var args = new String[]{
                "--inspect-content",
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--key-store", store.toString(),
                "--output-file", backup.resolve("backup-content.csv").toString(),
                "--at-epoch-seconds", Long.MAX_VALUE + ""
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);
        final var underTest = spy(new Controller(args, console));
        doNothing().when(underTest).doInspectContent(any());

        //when
        underTest.run();

        //then
        verify(underTest).doInspectContent(any());
    }

    @Test
    void testInspectIncrementsShouldBeCalledWhenRequiredRestoreParametersArePassed() throws Exception {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var password = new char[]{'a', 'b', 'c'};
        final var args = new String[]{
                "--inspect-increments",
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--key-store", store.toString()
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);
        final var underTest = spy(new Controller(args, console));
        doNothing().when(underTest).doInspectIncrements(any());

        //when
        underTest.run();

        //then
        verify(underTest).doInspectIncrements(any());
    }

    @Test
    void testDeleteIncrementsShouldBeCalledWhenRequiredRestoreParametersArePassed() throws Exception {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var password = new char[]{'a', 'b', 'c'};
        final var args = new String[]{
                "--delete",
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--key-store", store.toString(),
                "--from-epoch-seconds", Long.MAX_VALUE + ""
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);
        final var underTest = spy(new Controller(args, console));
        doNothing().when(underTest).doDeleteIncrements(any());

        //when
        underTest.run();

        //then
        verify(underTest).doDeleteIncrements(any());
    }
}
