package com.github.nagyesta.filebarj.io.stream.internal;

import lombok.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.Mockito.mock;

class DoOnCloseInputStreamTest {

    private static final int WAIT_MILLIS = 1000;
    private static final int THREAD_COUNT = 5;
    private static final byte[] CONTENT_BYTES = "content".getBytes();

    @Test
    void testReadSingleIntShouldReadFromTheUnderLyingStreamWhenItIsOpen() throws IOException {
        //given
        final var bytes = CONTENT_BYTES;
        final var stream = new ByteArrayInputStream(bytes);
        final var underTest = new TestDoOnCloseInputStream(stream);

        //when
        final var read = underTest.read();

        //then
        Assertions.assertEquals(bytes[0], read);
    }

    @Test
    void testReadSingleIntShouldThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT_BYTES);
        final var underTest = new TestDoOnCloseInputStream(stream);
        underTest.close();

        //when
        Assertions.assertThrows(IOException.class, underTest::read);

        //then + exception
    }

    @Test
    void testReadByteArrayShouldReadFromTheUnderLyingStreamWhenItIsOpen() throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT_BYTES);
        final var underTest = new TestDoOnCloseInputStream(stream);
        final var result = new byte[1];

        //when
        final var readCount = underTest.read(result);

        //then
        Assertions.assertEquals(1, readCount);
        Assertions.assertEquals(CONTENT_BYTES[0], result[0]);
    }

    @Test
    void testReadByteArrayShouldThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT_BYTES);
        final var underTest = new TestDoOnCloseInputStream(stream);
        underTest.close();
        final var result = new byte[1];

        //when
        Assertions.assertThrows(IOException.class, () -> underTest.read(result));

        //then + exception
    }

    @Test
    void testReadByteArrayWithRegionShouldReadFromTheUnderLyingStreamWhenItIsOpen() throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT_BYTES);
        final var underTest = new TestDoOnCloseInputStream(stream);
        final var result = new byte[1];

        //when
        final var readCount = underTest.read(result, 0, 1);

        //then
        Assertions.assertEquals(1, readCount);
        Assertions.assertEquals(CONTENT_BYTES[0], result[0]);
    }

    @Test
    void testReadByteArrayWithRegionShouldThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT_BYTES);
        final var underTest = new TestDoOnCloseInputStream(stream);
        underTest.close();
        final var result = new byte[1];

        //when
        Assertions.assertThrows(IOException.class, () -> underTest.read(result, 0, 1));

        //then + exception
    }

    @Test
    void testDoOnCloseShouldBeCalledWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = new ByteArrayInputStream(CONTENT_BYTES);
        final var underTest = new TestDoOnCloseInputStream(stream);
        Assertions.assertEquals(0, underTest.onCloseCalled);

        //when
        underTest.close();

        //then
        Assertions.assertEquals(1, underTest.onCloseCalled);
    }

    @Test
    void testDoOnCloseShouldBeCalledOnlyOnceWhenTheStreamIsClosedMoreThanOnce() throws IOException {
        //given
        final var stream = mock(InputStream.class);
        final var underTest = new TestDoOnCloseInputStream(stream);
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
        final var stream = mock(InputStream.class);
        final var underTest = new TestDoOnCloseInputStream(stream);

        //when
        Assertions.assertThrows(IllegalStateException.class, underTest::assertClosed);

        //then + exception
    }

    @Test
    void testAssertClosedShouldNotThrowExceptionWhenTheStreamIsClosed() throws IOException {
        //given
        final var stream = mock(InputStream.class);
        final var underTest = new TestDoOnCloseInputStream(stream);
        underTest.close();

        //when
        Assertions.assertDoesNotThrow(underTest::assertClosed);

        //then
        Assertions.assertTrue(underTest.isClosed());
    }

    @Test
    void testCloseShouldUseLockToAvoidRaceConditionWhenTheStreamIsClosedFromMultipleThreads() {
        //given
        final var stream = mock(InputStream.class);
        //noinspection NullableProblems
        final var underTest = new TestDoOnCloseInputStream(stream) {
            @Override
            protected @NonNull InputStream getInputStream() {
                Assertions.assertDoesNotThrow(() -> Thread.sleep(WAIT_MILLIS));
                return super.getInputStream();
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
    private static class TestDoOnCloseInputStream extends DoOnCloseInputStream {
        private final InputStream stream;
        int onCloseCalled;

        TestDoOnCloseInputStream(final InputStream stream) {
            this.stream = stream;
            onCloseCalled = 0;
        }

        @Override
        protected @NonNull InputStream getInputStream() {
            return stream;
        }

        @Override
        protected void doOnClose() {
            onCloseCalled++;
        }
    }
}
