package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoFunction;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompositeArchiveStreamTest {

    private static final String SHA_256 = "SHA-256";
    private static final byte[] CONTENT_BYTES = "content".getBytes(StandardCharsets.UTF_8);

    @Test
    void testConstructorShouldCloseIntermediateStreamsWhenAnExceptionIsThrownDuringTheirCreation() throws IOException {
        //given
        final var destination = mock(OutputStream.class);
        final IoFunction<OutputStream, OutputStream> function = out -> {
            throw new IllegalStateException("Test exception");
        };

        //when
        Assertions.assertThrows(IllegalStateException.class,
                () -> new CompositeArchiveStream(destination, SHA_256, function));

        //then
        verify(destination, atLeast(2)).close();
    }

    @Test
    void testGetByteCountShouldCalculateContentLengthWhenTheStreamIsWritten() throws IOException {
        //given
        final var destinationStream = mock(OutputStream.class);
        final var underTest = new CompositeArchiveStream(destinationStream, SHA_256);
        final var content = CONTENT_BYTES;

        //when
        underTest.write(content);
        underTest.close();
        final var actual = underTest.getByteCount();

        //then
        Assertions.assertEquals(content.length, actual);
        verify(destinationStream).write(eq(content), eq(0), eq(content.length));
    }

    @Test
    void testGetByteCountShouldThrowExceptionWhenTheStreamIsNotClosedYet() throws IOException {
        //given
        final var destinationStream = mock(OutputStream.class);
        final var underTest = new CompositeArchiveStream(destinationStream, SHA_256);
        final var content = CONTENT_BYTES;

        //when
        underTest.write(content);
        Assertions.assertThrows(IllegalStateException.class, underTest::getByteCount);

        //then + exception
        verify(destinationStream).write(eq(content), eq(0), eq(content.length));
    }

    @Test
    void testGetDigestValueShouldCalculateDigestOfContentWhenTheStreamIsWritten() throws IOException {
        //given
        final var destinationStream = mock(OutputStream.class);
        final var underTest = new CompositeArchiveStream(destinationStream, SHA_256);
        final var content = CONTENT_BYTES;
        final var expected = DigestUtils.sha256Hex(content);

        //when
        underTest.write(content);
        underTest.close();
        final var actual = underTest.getDigestValue();

        //then
        Assertions.assertEquals(expected, actual);
        verify(destinationStream).write(eq(content), eq(0), eq(content.length));
    }

    @Test
    void testGetDigestValueShouldThrowExceptionWhenTheStreamIsNotClosedYet() throws IOException {
        //given
        final var destinationStream = mock(OutputStream.class);
        final var underTest = new CompositeArchiveStream(destinationStream, SHA_256);
        final var content = CONTENT_BYTES;

        //when
        underTest.write(content);
        Assertions.assertThrows(IllegalStateException.class, underTest::getDigestValue);

        //then + exception
        verify(destinationStream).write(eq(content), eq(0), eq(content.length));
    }

    @Test
    void testCloseShouldCloseDestinationStream() throws IOException {
        //given
        final var destinationStream = mock(OutputStream.class);
        final var underTest = new CompositeArchiveStream(destinationStream, SHA_256);

        //when
        underTest.close();

        //then
        verify(destinationStream, atLeastOnce()).close();
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDestination() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CompositeArchiveStream(null, SHA_256));

        //then + exception
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullCompressionFunction() {
        //given
        final var stream = mock(OutputStream.class);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CompositeArchiveStream(stream, SHA_256, null));

        //then + exception
    }

}
