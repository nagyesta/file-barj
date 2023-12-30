package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;

class BarjCargoArchiveEntryIteratorTest {

    private static Stream<Arguments> nullProviderNoPaths() {
        final var streamSource = mock(BarjCargoArchiveFileInputStreamSource.class);
        final var empty = Collections.emptyList();
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, null))
                .add(Arguments.of(streamSource, null))
                .add(Arguments.of(null, empty))
                .build();
    }

    static Stream<Arguments> nullProvider() {
        final var streamSource = mock(BarjCargoArchiveFileInputStreamSource.class);
        final var empty = Collections.emptyList();
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, null, null))
                .add(Arguments.of(streamSource, null, null))
                .add(Arguments.of(null, empty, null))
                .add(Arguments.of(null, null, empty))
                .add(Arguments.of(null, empty, empty))
                .add(Arguments.of(streamSource, null, empty))
                .add(Arguments.of(streamSource, empty, null))
                .build();
    }

    @SuppressWarnings("resource")
    @ParameterizedTest
    @MethodSource("nullProviderNoPaths")
    void testConstructorShouldThrowExceptionWhenCalledWithoutRelevantFiles(
            final BarjCargoArchiveFileInputStreamSource source,
            final List<BarjCargoEntityIndex> list) {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BarjCargoArchiveEntryIterator(source, list));

        //then + exception
    }

    @SuppressWarnings("resource")
    @ParameterizedTest
    @MethodSource("nullProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithRelevantFiles(
            final BarjCargoArchiveFileInputStreamSource source,
            final List<Path> relevantFiles,
            final List<BarjCargoEntityIndex> list) {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BarjCargoArchiveEntryIterator(source, relevantFiles, list));

        //then + exception
    }
}
