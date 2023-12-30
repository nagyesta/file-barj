package com.github.nagyesta.filebarj.io.stream.internal;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

class FixedRangeInputStreamTest {

    @SuppressWarnings("checkstyle:MagicNumber")
    public static Stream<Arguments> rangeProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of("0123456789", 0, 10, "0123456789"))
                .add(Arguments.of("0123456789", 0, 5, "01234"))
                .add(Arguments.of("0123456789", 0, 3, "012"))
                .add(Arguments.of("0123456789", 0, 1, "0"))
                .add(Arguments.of("0123456789", 3, 2, "34"))
                .add(Arguments.of("0123456789", 3, 7, "3456789"))
                .add(Arguments.of("0123456789", 3, 10, "3456789"))
                .build();
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenSourceIsNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FixedRangeInputStream(null, 0, 1));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenLengthIsNegative() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FixedRangeInputStream(InputStream.nullInputStream(), 0, -1));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenStartIndexIsNegative() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FixedRangeInputStream(InputStream.nullInputStream(), -1, 1));

        //then + exception
    }

    @ParameterizedTest
    @MethodSource("rangeProvider")
    void testReadAllBytesShouldSkipStartingAndTrailingBytesWhenStartingIndexIsNotZeroAndLengthIsShorterThanRemainingBytes(
            final String input, final int startInclusive, final int length, final String expected) throws IOException {
        //given
        final var source = new ByteArrayInputStream(input.getBytes());
        final var underTest = new FixedRangeInputStream(source, startInclusive, length);

        //when
        final var actual = underTest.readAllBytes();

        //then
        final var actualString = new String(actual);
        Assertions.assertEquals(expected, actualString);
    }

    @ParameterizedTest
    @MethodSource("rangeProvider")
    void testReadShouldSkipStartingAndTrailingBytesWhenStartingIndexIsNotZeroAndLengthIsShorterThanRemainingBytes(
            final String input, final int startInclusive, final int length, final String expected) throws IOException {
        //given
        final var source = new ByteArrayInputStream(input.getBytes());
        final var underTest = new FixedRangeInputStream(source, startInclusive, length);

        //when
        for (final var expectedByte : expected.getBytes()) {
            final var actual = underTest.read();

            //then
            Assertions.assertEquals(expectedByte, actual);
        }
        Assertions.assertEquals(IOUtils.EOF, underTest.read());
    }

    @ParameterizedTest
    @MethodSource("rangeProvider")
    void testReadBytesShouldSkipStartingAndTrailingBytesWhenStartingIndexIsNotZeroAndLengthIsShorterThanRemainingBytes(
            final String input, final int startInclusive, final int length, final String expected) throws IOException {
        //given
        final var source = new ByteArrayInputStream(input.getBytes());
        final var underTest = new FixedRangeInputStream(source, startInclusive, length);

        //when
        final var actual = new byte[expected.length()];
        final var actualCount = underTest.read(actual);

        //then
        Assertions.assertEquals(expected.length(), actualCount);
        final var actualString = new String(actual);
        Assertions.assertEquals(expected, actualString);
        Assertions.assertEquals(IOUtils.EOF, underTest.read(new byte[1]));
    }
}
