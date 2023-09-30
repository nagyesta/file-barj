package com.github.nagyesta.filebarj.io.stream.model;

import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveEntryIterator;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SequentialBarjCargoArchiveEntryTest {

    public static Stream<Arguments> nullProvider() {
        final var source = mock(BarjCargoArchiveFileInputStreamSource.class);
        final var iterator = mock(BarjCargoArchiveEntryIterator.class);
        final var entityIndex = mock(BarjCargoEntityIndex.class);
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, null, null))
                .add(Arguments.of(source, null, null))
                .add(Arguments.of(null, iterator, null))
                .add(Arguments.of(null, null, entityIndex))
                .add(Arguments.of(null, iterator, entityIndex))
                .add(Arguments.of(source, null, entityIndex))
                .add(Arguments.of(source, iterator, null))
                .build();
    }

    @ParameterizedTest
    @MethodSource("nullProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithNulls(
            final BarjCargoArchiveFileInputStreamSource source,
            final BarjCargoArchiveEntryIterator iterator,
            final BarjCargoEntityIndex entityIndex) {
        //given

        //when
        assertThrows(IllegalArgumentException.class, () -> new SequentialBarjCargoArchiveEntry(source, iterator, entityIndex));

        //then + exception
    }
}
