package com.github.nagyesta.filebarj.core.config.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

class CompressionAlgorithmTest {

    public static Stream<Arguments> compressionAlgorithmProvider() {
        return Arrays.stream(CompressionAlgorithm.values())
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("compressionAlgorithmProvider")
    void testDecompressionStreamShouldReturnTheOriginalContentWhenCalledOnTheResultOfTheCompression(
            final CompressionAlgorithm underTest) throws IOException {
        //given
        final var out = new ByteArrayOutputStream();
        final var input = "Lorem ipsum".getBytes(StandardCharsets.UTF_8);
        final var compressor = underTest.decorateOutputStream(out);
        compressor.write(input);
        compressor.flush();
        compressor.close();
        final var compressed = out.toByteArray();
        final var in = new ByteArrayInputStream(compressed);

        //when
        final var decompressor = underTest.decorateInputStream(in);
        final var actual = decompressor.readAllBytes();

        //then
        Assertions.assertArrayEquals(input, actual);
    }
}
