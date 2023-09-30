package com.github.nagyesta.filebarj.job.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class CliBackupParserTest {

    @Test
    void testConstructorShouldThrowExceptionWhenNoArgsArePassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliBackupParser(new String[0]));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenUnknownOptionIsPassed() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliBackupParser(new String[]{"--unknown"}));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenRequiredParameterIsMissing() {
        //given
        final var threads = 2;
        final var args = new String[] {"--threads", String.valueOf(threads)};

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CliBackupParser(args));

        //then + exception
    }

    @Test
    void testConstructorShouldCollectAndSetConfigurationValuesWhenAllArgsArePassed() {
        //given
        final var configPath = Path.of("config.json");
        final var threads = 2;
        final var args = new String[] {"--config", configPath.toString(), "--threads", String.valueOf(threads)};

        //when
        final var underTest = new CliBackupParser(args);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(configPath.toAbsolutePath(), actual.getConfig());
        Assertions.assertEquals(threads, actual.getThreads());
    }

    @Test
    void testConstructorShouldCollectAndSetConfigurationValuesWhenRequiredArgsArePassed() {
        //given
        final var configPath = Path.of("config.json");
        final var threads = 1;
        final var args = new String[] {"--config", configPath.toString()};

        //when
        final var underTest = new CliBackupParser(args);
        final var actual = underTest.getResult();

        //then
        Assertions.assertEquals(configPath.toAbsolutePath(), actual.getConfig());
        Assertions.assertEquals(threads, actual.getThreads());
    }
}
