package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.IoSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.apache.commons.io.input.UnsynchronizedByteArrayInputStream.END_OF_STREAM;

class MergingInputStreamTest {

    private static final int WAIT_MILLIS = 1000;
    private static final int THREAD_COUNT = 5;

    @SuppressWarnings({"DataFlowIssue", "resource"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullStreamList() throws IOException {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new MergingInputStream(null, null));

        //then + exception
    }

    @Test
    void testReadAllBytesShouldReadAllBytesAcrossTheChunksWhenCalledWithAListOfStreams() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        final var expectedBytes = getInputStream().readAllBytes();
        try (var underTest = new MergingInputStream(allStreams, null)) {
            //when
            final var actualBytes = underTest.readAllBytes();

            //then
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        }
    }

    @Test
    void testRepeatedReadsShouldReadAllBytesAcrossTheChunksWhenCalledWithAListOfStreams() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        final var expectedBytes = getInputStream().readAllBytes();
        try (var underTest = new MergingInputStream(allStreams, null)) {
            for (final var expectedByte : expectedBytes) {
                //when
                final var actualByte = underTest.read();

                //then
                Assertions.assertEquals(expectedByte, actualByte);
            }
            Assertions.assertEquals(END_OF_STREAM, underTest.read());
        }
    }

    @Test
    void testRepeatedReadsShouldReduceTheRemainingBytesWhenCalledWithAListOfStreams() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        final var expectedBytes = getInputStream().readAllBytes();
        var remaining = expectedBytes.length;
        try (var underTest = new MergingInputStream(allStreams, (long) remaining)) {
            for (final var expectedByte : expectedBytes) {
                //when
                final var actualByte = underTest.read();
                final var availableBytesEstimate = underTest.available();

                //then
                Assertions.assertEquals(--remaining, availableBytesEstimate);
                Assertions.assertEquals(expectedByte, actualByte);
            }
            Assertions.assertEquals(0, underTest.available());
        }
    }

    @Test
    void testReadShouldReturnEndOfStreamWhenStreamIsClosed() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        try (var underTest = new MergingInputStream(allStreams, null)) {
            underTest.close();
            //when
            final var actual = underTest.read();
            //then
            Assertions.assertEquals(END_OF_STREAM, actual);
        }
    }

    @Test
    void testReadBytesShouldReturnEndOfStreamWhenStreamIsClosed() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        try (var underTest = new MergingInputStream(allStreams, null)) {
            underTest.close();
            //when
            final var actual = underTest.read(new byte[1], 0, 1);
            //then
            Assertions.assertEquals(END_OF_STREAM, actual);
        }
    }

    @Test
    void testAvailableShouldReturnZeroWhenStreamIsClosed() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        try (var underTest = new MergingInputStream(allStreams, (long) 1)) {
            underTest.close();
            //when
            final var actual = underTest.available();
            //then
            Assertions.assertEquals(0, actual);
        }
    }

    @Test
    void testRepeatedByteArrayReadsShouldReadAllBytesAcrossTheChunksWhenCalledWithAListOfStreams() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        final var expectedBytes = getInputStream().readAllBytes();
        final var actualBytes = new byte[(int) expectedBytes.length];
        try (var underTest = new MergingInputStream(allStreams, null)) {
            var pos = 0;
            while (pos < actualBytes.length) {
                //when
                final var actualReadCount = underTest.read(actualBytes, pos, actualBytes.length - pos);
                pos += actualReadCount;
                //then
                Assertions.assertNotEquals(END_OF_STREAM, actualReadCount);
            }
            //assert that the stream has reached the end (should not change the byte array)
            Assertions.assertEquals(END_OF_STREAM, underTest.read(actualBytes, actualBytes.length - 1, 1));
            //compare the byte arrays
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        }
    }

    @Test
    void testSkipShouldSkipBytesAcrossTheChunksSameWayAsInASingleStreamWhenCalledWithAListOfStreams() throws IOException {
        //given
        final var allStreams = getIoSupplierList();
        try (var underTest = new MergingInputStream(allStreams, null);
             var expectedStream = getInputStream()) {
            //when
            final var actualReturnValue = underTest.skip(10);
            final var actualBytes = underTest.readNBytes(50);
            final var expectedReturnValue = expectedStream.skip(10);
            final var expectedBytes = expectedStream.readNBytes(50);

            //then
            Assertions.assertEquals(expectedReturnValue, actualReturnValue);
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        }
    }


    @Test
    void testCloseShouldUseLockToAvoidRaceConditionWhenTheStreamIsClosedFromMultipleThreads() throws IOException {
        //given
        final var allStreams = List.of(delayCloseStream());
        final var underTest = new MergingInputStream(allStreams, null);
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
        Assertions.assertTrue(underTest.isClosed());
    }

    private IoSupplier<InputStream> delayCloseStream() {
        return new IoSupplier<>() {
            @SuppressWarnings("NullableProblems")
            @Override
            public InputStream get() throws IOException {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return 0;
                    }

                    @Override
                    public void close() throws IOException {
                        Assertions.assertDoesNotThrow(() -> Thread.sleep(WAIT_MILLIS));
                        super.close();
                    }
                };
            }
        };
    }

    private InputStream getInputStream() {
        return Objects.requireNonNull(getClass()
                .getResourceAsStream("/chunks/a.txt"));
    }

    private List<IoSupplier<InputStream>> getIoSupplierList() {
        return Stream.of("a-1.txt", "a-2.txt", "a-3.txt", "a-4.txt")
                .map(s -> "/chunks/" + s)
                .map(getClass()::getResourceAsStream)
                .filter(Objects::nonNull)
                .map(s -> (IoSupplier<InputStream>) () -> s)
                .toList();
    }
}
