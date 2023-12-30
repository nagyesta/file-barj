package com.github.nagyesta.filebarj.io.stream.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;

import static org.mockito.Mockito.*;

class CloseShieldingOutputStreamTest {

    @SuppressWarnings("resource")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CloseShieldingOutputStream(null));

        //then + exception
    }

    @Test
    void testWriteShouldWriteToTheUnderLyingStreamOpenWhenCalled() throws IOException {
        //given
        final OutputStream destination = mock();
        final var underTest = new CloseShieldingOutputStream(destination);

        //when
        underTest.write(1);

        //then
        verify(destination).write(1);
    }

    @Test
    void testCloseShouldFlushAndKeepUnderLyingStreamOpenWhenCalled() throws IOException {
        //given
        final OutputStream destination = mock();
        final var underTest = new CloseShieldingOutputStream(destination);

        //when
        underTest.close();

        //then
        verify(destination, never()).close();
        verify(destination).flush();
    }

    @Test
    void testCloseStreamShouldFlushAndCloseUnderLyingStreamWhenCalled() throws IOException {
        //given
        final OutputStream destination = mock();
        final var underTest = new CloseShieldingOutputStream(destination);

        //when
        underTest.closeStream();

        //then
        final var inOrder = inOrder(destination);
        inOrder.verify(destination).flush();
        inOrder.verify(destination).close();
    }
}
