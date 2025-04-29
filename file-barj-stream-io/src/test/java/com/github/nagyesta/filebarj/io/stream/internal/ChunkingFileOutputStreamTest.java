package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.TempFileAwareTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Stream;

import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingFileOutputStream.MEBIBYTE;

class ChunkingFileOutputStreamTest extends TempFileAwareTest {

    private static final Random RANDOM = new Random();

    public static Stream<Arguments> nullProvider() {
        final var path = Path.of("folder");
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, "prefix"))
                .add(Arguments.of(path, null))
                .build();
    }

    @SuppressWarnings({"checkstyle:MagicNumber", "MagicNumber"})
    public static Stream<Arguments> chunkingDataProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of("single-small", 1, 100, 1))
                .add(Arguments.of("10-small-chunks", 1, 10 * MEBIBYTE, 10))
                .add(Arguments.of("2-big-chunks", 10, 20 * MEBIBYTE, 2))
                .add(Arguments.of("single-medium", 5, 4 * MEBIBYTE, 1))
                .add(Arguments.of("2-medium-chunks", 6, 10 * MEBIBYTE, 2))
                .build();
    }

    @SuppressWarnings("resource")
    @ParameterizedTest
    @MethodSource("nullProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithNull(
            final Path folder,
            final String prefix) {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChunkingFileOutputStream(folder, prefix, 1));

        //then + exception
    }

    @ParameterizedTest
    @MethodSource("chunkingDataProvider")
    void testTotalByteCountShouldReturnAccurateCountWhenChunkingIsActive(
            final String name,
            final int chunkSize,
            final long randomDataSize,
            final int numberOfTimes) {
        //given
        final var directory = Path.of(getTestDataRoot().toString(), name);
        final var expectedBytes = new byte[(int) randomDataSize];
        RANDOM.nextBytes(expectedBytes);
        try (var underTest = new ChunkingFileOutputStream(
                directory, name + "-file.out", chunkSize)) {
            //when
            final var actualBytesWritten = new ArrayList<Long>();
            for (var i = 0; i < numberOfTimes; i++) {
                underTest.write(expectedBytes);
                actualBytesWritten.add(underTest.getTotalByteCount());
            }
            underTest.flush();

            //then
            for (var i = 0; i < numberOfTimes; i++) {
                Assertions.assertEquals(randomDataSize * (i + 1L), actualBytesWritten.get(i));
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("chunkingDataProvider")
    void testChunkingShouldActivateWhenCalledWithEnoughRandomData(
            final String name,
            final int chunkSize,
            final long randomDataSize,
            final int expectedFiles) {
        //given
        final var directory = Path.of(getTestDataRoot().toString(), name);
        final var expectedBytes = new byte[(int) randomDataSize];
        RANDOM.nextBytes(expectedBytes);
        try (var underTest = new ChunkingFileOutputStream(
                directory, name + "-file.out", chunkSize)) {
            //when
            underTest.write(expectedBytes);
            final var actualLastFilePath = underTest.getCurrentFilePath();
            underTest.flush();

            //then
            final var actualWritten = underTest.getDataFilesWritten();
            Assertions.assertEquals(expectedFiles, actualWritten.size());
            Assertions.assertEquals(actualWritten.get(actualWritten.size() - 1), actualLastFilePath);
            final var actualBytes = new byte[(int) randomDataSize];
            var copiedSoFar = 0;
            for (final var path : actualWritten) {
                try {
                    final var read = IOUtils.toByteArray(path.toUri());
                    System.arraycopy(read, 0, actualBytes, copiedSoFar, read.length);
                    copiedSoFar += read.length;
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("chunkingDataProvider")
    void testChunkingShouldWorkWhenCalledWithBatchesOfRandomDataNotEndingAtThreshold(
            final String name,
            final int chunkSize,
            final long randomDataSize) {
        //given
        final var directory = Path.of(getTestDataRoot().toString(), name);
        final var offsetToMisalignData = 10;
        final var expectedBytes = new byte[(int) randomDataSize + offsetToMisalignData];
        RANDOM.nextBytes(expectedBytes);
        try (var underTest = new ChunkingFileOutputStream(
                directory, name + "-file.out", chunkSize)) {
            //when
            underTest.write(expectedBytes, 0, offsetToMisalignData);
            underTest.write(expectedBytes, offsetToMisalignData, (int) randomDataSize);
            underTest.flush();

            //then
            final var actualWritten = underTest.getDataFilesWritten();
            final var actualBytes = new byte[(int) randomDataSize + offsetToMisalignData];
            var copiedSoFar = 0;
            for (final var path : actualWritten) {
                try {
                    final var read = IOUtils.toByteArray(path.toUri());
                    System.arraycopy(read, 0, actualBytes, copiedSoFar, read.length);
                    copiedSoFar += read.length;
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
