package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.exception.ArchiveIntegrityException;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

class SelfValidatingOptionalDigestInputStreamTest {

    private static final String SHA_256 = "SHA-256";
    public static final String CONTENT = "content";

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDestination() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SelfValidatingOptionalDigestInputStream(null, SHA_256, ""));

        //then + exception
    }

    @Test
    void testCloseShouldOnlyCloseTheStreamWhenTheDigestAlgorithmIsNull() {
        //given
        final var stream = InputStream.nullInputStream();
        final var underTest = new SelfValidatingOptionalDigestInputStream(stream, null, null);

        //when
        Assertions.assertDoesNotThrow(underTest::close);

        //then + no exception
    }

    @ParameterizedTest
    @ValueSource(strings = {"/path/to/target", "content"})
    void testCloseShouldSuccessfullyVerifyDigestWhenTheDigestAlgorithmIsSetAndTheCorrectDigestIsProvided(
            final String content) throws IOException {
        //given
        final var contentBytes = content.getBytes();
        final var expectedDigest = DigestUtils.sha256Hex(contentBytes);
        final var stream = new ByteArrayInputStream(contentBytes);
        final var underTest = new SelfValidatingOptionalDigestInputStream(stream, SHA_256, expectedDigest);

        //when
        final var actualBytes = underTest.readAllBytes();
        Assertions.assertDoesNotThrow(underTest::close);

        //then
        Assertions.assertArrayEquals(contentBytes, actualBytes);
        Assertions.assertEquals(expectedDigest, underTest.getDigestValue());
    }

    @Test
    void testCloseShouldThrowExceptionWhenTheDigestAlgorithmIsSetAndTheWrongDigestIsProvided()
            throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT.getBytes());
        final var underTest = new SelfValidatingOptionalDigestInputStream(stream, SHA_256, "wrong");
        underTest.readAllBytes();

        //when
        Assertions.assertThrows(ArchiveIntegrityException.class, underTest::close);

        //then + exception
    }
}
