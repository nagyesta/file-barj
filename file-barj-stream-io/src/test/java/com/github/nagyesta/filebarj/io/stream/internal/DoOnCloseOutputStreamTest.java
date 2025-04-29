package com.github.nagyesta.filebarj.io.stream.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DoOnCloseOutputStreamTest {

    private static final int WAIT_MILLIS = 1000;
    private static final int THREAD_COUNT = 5;

    @Test
    void testWriteSingleIntShouldWriteToTheUnderLyingStreamWhenItIsOpen() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        final var content = 0x01;

        //when
        underTest.write(content);

        //then
        verify(stream).write(content);
    }

    @Test
    void testWriteSingleIntShouldThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        underTest.close();
        final var content = 0x01;

        //when
        Assertions.assertThrows(IOException.class, () -> underTest.write(content));

        //then + exception
    }

    @Test
    void testWriteByteArrayShouldWriteToTheUnderLyingStreamWhenItIsOpen() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        final var content = "content".getBytes();

        //when
        underTest.write(content);

        //then
        verify(stream).write(content);
    }

    @Test
    void testWriteByteArrayShouldThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        underTest.close();
        final var content = "content".getBytes();

        //when
        Assertions.assertThrows(IOException.class, () -> underTest.write(content));

        //then + exception
    }

    @Test
    void testWriteByteArrayWithRegionShouldWriteToTheUnderLyingStreamWhenItIsOpen() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        final var content = "content".getBytes();

        //when
        underTest.write(content, 0, 1);

        //then
        verify(stream).write(content, 0, 1);
    }

    @Test
    void testWriteByteArrayWithRegionShouldThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        underTest.close();
        final var content = "content".getBytes();

        //when
        Assertions.assertThrows(IOException.class, () -> underTest.write(content, 0, 1));

        //then + exception
    }

    @Test
    void testDoOnCloseShouldBeCalledWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        Assertions.assertEquals(0, underTest.onCloseCalled);

        //when
        underTest.close();

        //then
        Assertions.assertEquals(1, underTest.onCloseCalled);
    }

    @Test
    void testDoOnCloseShouldBeCalledOnlyOnceWhenTheStreamIsClosedMoreThanOnce() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        Assertions.assertEquals(0, underTest.onCloseCalled);

        //when
        underTest.close();
        underTest.close();
        underTest.close();

        //then
        Assertions.assertEquals(1, underTest.onCloseCalled);
    }

    @Test
    void testAssertClosedShouldThrowExceptionWhenTheStreamIsOpen() {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);

        //when
        Assertions.assertThrows(IllegalStateException.class, underTest::assertClosed);

        //then + exception
    }

    @Test
    void testAssertClosedShouldNotThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream);
        underTest.close();

        //when
        Assertions.assertDoesNotThrow(underTest::assertClosed);

        //then
        Assertions.assertTrue(underTest.isClosed());
    }

    @Test
    @SuppressWarnings("java:S1612")
    void testCloseShouldUseLockToAvoidRaceConditionWhenTheStreamIsClosedFromMultipleThreads() {
        //given
        final var stream = mock(OutputStream.class);
        final var underTest = new TestDoOnCloseOutputStream(stream) {
            @Override
            @SuppressWarnings("java:S2925")
            public void flush() {
                Assertions.assertDoesNotThrow(() -> Thread.sleep(WAIT_MILLIS));
            }
        };
        final var executor = Executors.newFixedThreadPool(THREAD_COUNT);

        //when
        final List<Future<?>> futures = new ArrayList<>();
        for (var i = 0; i < THREAD_COUNT; i++) {
            final var future = executor.submit(() -> Assertions.assertDoesNotThrow(underTest::close));
            futures.add(future);
        }
        futures.forEach(future -> Assertions.assertDoesNotThrow(() -> future.get()));
        executor.shutdown();

        //then
        Assertions.assertTrue(underTest.onCloseCalled > 0);
        Assertions.assertTrue(underTest.isClosed());
    }

    @SuppressWarnings("checkstyle:VisibilityModifier")
    private static class TestDoOnCloseOutputStream extends DoOnCloseOutputStream {
        private final OutputStream stream;
        int onCloseCalled;

        TestDoOnCloseOutputStream(final OutputStream stream) {
            this.stream = stream;
            onCloseCalled = 0;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        protected OutputStream getOutputStream() {
            return stream;
        }

        @Override
        protected void doOnClose() {
            onCloseCalled++;
        }
    }
}
