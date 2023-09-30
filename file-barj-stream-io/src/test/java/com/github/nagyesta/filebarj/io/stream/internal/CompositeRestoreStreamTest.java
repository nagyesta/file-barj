package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.mockito.Mockito.*;

class CompositeRestoreStreamTest {

    private static final String NULL_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String SHA_256 = "SHA-256";

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullSource() {
        //given
        final List<IoFunction<InputStream, InputStream>> steps = List.of();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CompositeRestoreStream(null, null, steps, null));

        //then + exception
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullStepsList() {
        //given
        final var source = mock(InputStream.class);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CompositeRestoreStream(source, null, null, null));

        //then + exception
    }

    @Test
    void testConstructorShouldCloseIntermediateStreamsWhenAnExceptionIsThrownDuringTheirCreation() throws IOException {
        //given
        final var source = mock(InputStream.class);
        final var transform1 = mock(InputStream.class);
        final var transform2 = mock(InputStream.class);
        final List<IoFunction<InputStream, InputStream>> steps = List.of(in -> transform1, in -> transform2);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CompositeRestoreStream(source, "test", steps, null));

        //then
        verify(source, atLeastOnce()).close();
        verify(transform1, atLeastOnce()).close();
        verify(transform2, atLeastOnce()).close();
    }

    @Test
    void testGetInputStreamShouldReturnTheBufferedInputStreamWhenCalled() throws IOException {
        //given
        final var source = mock(InputStream.class);
        final List<IoFunction<InputStream, InputStream>> steps = List.of();
        final var underTest = new CompositeRestoreStream(source, null, steps, null);

        //when
        final var result = underTest.getInputStream();

        //then
        Assertions.assertInstanceOf(BufferedInputStream.class, result);
    }

    @Test
    void testCloseShouldCloseIntermediateStreamsWhenCalled() throws IOException {
        //given
        final var source = mock(InputStream.class);
        final var transform1 = mock(InputStream.class);
        final var transform2 = mock(InputStream.class);
        final List<IoFunction<InputStream, InputStream>> steps = List.of(in -> transform1, in -> transform2);
        final var underTest = new CompositeRestoreStream(source, SHA_256, steps, NULL_HASH);

        //when
        underTest.close();

        //then
        verify(source, atLeastOnce()).close();
        verify(transform1, atLeastOnce()).close();
        verify(transform2, atLeastOnce()).close();
    }

}
