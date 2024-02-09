package com.github.nagyesta.filebarj.job.cli;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Console;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;

class CliRestoreParserTest {

    @Test
    void testConstructorShouldThrowExceptionWhenNoArgsArePassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliRestoreParser(new String[0], mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenUnknownOptionIsPassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliRestoreParser(new String[]{"--unknown"}, mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenRequiredParameterIsMissing() {
        //given
        final var threads = 2;
        final var args = new String[] {"--threads", String.valueOf(threads)};

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliRestoreParser(args, mock(Console.class)));

        //then + exception
    }

    @Test
    void testConstructorShouldNotTouchTheConsoleWhenKeyStoreParametersAreNotPassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var source = Path.of("source-dir");
        final var target = Path.of("target-dir");
        final var dryRun = true;
        final var threads = 2;
        final var args = new String[] {
                "--threads", String.valueOf(threads),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--dry-run", String.valueOf(dryRun),
                "--target-mapping", source + "=" + target
        };
        final var console = mock(Console.class);

        //when
        new CliRestoreParser(args, console);

        //then
        verifyNoInteractions(console);
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenKeyStoreParametersAreNotPassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var source = BackupPath.ofPathAsIs("source-dir");
        final var target = Path.of("target-dir");
        final var dryRun = true;
        final var threads = 2;
        final var args = new String[] {
                "--threads", String.valueOf(threads),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--dry-run", String.valueOf(dryRun),
                "--target-mapping", source + "=" + target
        };
        final var console = mock(Console.class);

        //when
        final var underTest = new CliRestoreParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(threads, actual.getThreads());
        Assertions.assertEquals(backup.toAbsolutePath(), actual.getBackupSource());
        Assertions.assertEquals(prefix, actual.getPrefix());
        Assertions.assertEquals(dryRun, actual.isDryRun());
        Assertions.assertEquals(Set.of(source), actual.getTargets().keySet());
        Assertions.assertEquals(target.toAbsolutePath(), actual.getTargets().get(source));
        Assertions.assertNull(actual.getKeyProperties());
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenAllKeyStoreParametersArePassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var source = BackupPath.ofPathAsIs("source-dir");
        final var target = Path.of("target-dir");
        final var store = Path.of("key-store.p12");
        final var dryRun = true;
        final var threads = 2;
        final var alias = "alias";
        final var password = new char[] {'a', 'b', 'c'};
        final var args = new String[] {
                "--threads", String.valueOf(threads),
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--dry-run", String.valueOf(dryRun),
                "--target-mapping", source + "=" + target,
                "--key-store", store.toString(),
                "--key-alias", alias,
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);

        //when
        final var underTest = new CliRestoreParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(threads, actual.getThreads());
        Assertions.assertEquals(backup.toAbsolutePath(), actual.getBackupSource());
        Assertions.assertEquals(prefix, actual.getPrefix());
        Assertions.assertEquals(dryRun, actual.isDryRun());
        Assertions.assertEquals(Set.of(source), actual.getTargets().keySet());
        Assertions.assertEquals(target.toAbsolutePath(), actual.getTargets().get(source));
        Assertions.assertNotNull(actual.getKeyProperties());
        Assertions.assertEquals(store.toAbsolutePath(), actual.getKeyProperties().getKeyStore());
        Assertions.assertEquals(alias, actual.getKeyProperties().getAlias());
        Assertions.assertArrayEquals(password, actual.getKeyProperties().getPassword());
    }

    @Test
    void testConstructorShouldCaptureAndSetPropertiesWhenRequiredKeyStoreParametersArePassed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var store = Path.of("key-store.p12");
        final var dryRun = false;
        final var threads = 1;
        final var alias = "default";
        final var password = new char[] {'a', 'b', 'c'};
        final var args = new String[] {
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--key-store", store.toString()
        };
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);

        //when
        final var underTest = new CliRestoreParser(args, console);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(threads, actual.getThreads());
        Assertions.assertEquals(backup.toAbsolutePath(), actual.getBackupSource());
        Assertions.assertEquals(prefix, actual.getPrefix());
        Assertions.assertEquals(dryRun, actual.isDryRun());
        Assertions.assertEquals(Map.of(), actual.getTargets());
        Assertions.assertNotNull(actual.getKeyProperties());
        Assertions.assertEquals(store.toAbsolutePath(), actual.getKeyProperties().getKeyStore());
        Assertions.assertEquals(alias, actual.getKeyProperties().getAlias());
        Assertions.assertArrayEquals(password, actual.getKeyProperties().getPassword());
    }

    @Test
    void testConstructorShouldThrowExceptionWhenTargetMappingIsMalformed() {
        //given
        final var prefix = "prefix";
        final var backup = Path.of("backup-dir");
        final var args = new String[] {
                "--backup-source", backup.toString(),
                "--prefix", prefix,
                "--target-mapping", "source",
                "--target-mapping", "target=1=2",
        };
        final var console = mock(Console.class);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliRestoreParser(args, console));

        //then + exception
    }
}
