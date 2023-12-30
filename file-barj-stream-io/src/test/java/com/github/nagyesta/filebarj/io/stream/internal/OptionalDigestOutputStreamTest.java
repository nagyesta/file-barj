package com.github.nagyesta.filebarj.io.stream.internal;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingFileOutputStream.MEBIBYTE;

class OptionalDigestOutputStreamTest {

    private static final int LONG_STREAM_MIB = 2100;

    @Test
    void testGetDigestValueShouldReturnNullWhenCalledWithNoneAlgorithm() throws IOException {
        //given
        final String alg = null;
        final var underTest = new OptionalDigestOutputStream(OutputStream.nullOutputStream(), alg);
        underTest.write("test".getBytes(StandardCharsets.UTF_8));

        //when
        final var actual = underTest.getDigestValue();

        //then
        Assertions.assertNull(actual);
    }

    @Test
    void testGetDigestValueShouldReturnDigestWhenCalledWithSha256Algorithm() throws IOException {
        //given
        final var alg = "sha-256";
        final var underTest = new OptionalDigestOutputStream(OutputStream.nullOutputStream(), alg);
        final var bytes = "test".getBytes(StandardCharsets.UTF_8);
        underTest.write(bytes);
        final var expected = DigestUtils.sha256Hex(bytes);

        //when
        final var actual = underTest.getDigestValue();

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testGetDigestValueShouldReturnDigestWhenCalledWithSha256AlgorithmWithAVeryLongStream() throws IOException {
        //given
        final var alg = "sha-256";
        final var underTest = new OptionalDigestOutputStream(OutputStream.nullOutputStream(), alg);
        final var bytes = new byte[(int) MEBIBYTE];
        final var digest = DigestUtils.getSha256Digest();
        for (var i = 0; i < LONG_STREAM_MIB; i++) {
            digest.update(bytes);
            underTest.write(bytes);
        }
        final var expected = Hex.encodeHexString(digest.digest(), true);

        //when
        final var actual = underTest.getDigestValue();

        //then
        Assertions.assertEquals(expected, actual);
    }
}
