package com.github.nagyesta.filebarj.io.stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BarjCargoUtilTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testToChunkFileNameShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> BarjCargoUtil.toChunkFileName(null, 1));

        //then + exception
    }

    @Test
    void testToChunkFileNameShouldUseLeadingZeroesWhenCalledWithNull() {
        //given

        //when
        final var actual = BarjCargoUtil.toChunkFileName("prefix", 1);

        //then
        Assertions.assertEquals("prefix.00001.cargo", actual);
    }

    @Test
    void testToChunkFileNameShouldThrowExceptionWhenCalledWithNegativeNumber() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> BarjCargoUtil.toChunkFileName("prefix", -1));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testToIndexFileNameShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> BarjCargoUtil.toIndexFileName(null));

        //then + exception
    }

    @Test
    void testToIndexFileNameShouldUseLeadingZeroesWhenCalledWithNull() {
        //given

        //when
        final var actual = BarjCargoUtil.toIndexFileName("prefix");

        //then
        Assertions.assertEquals("prefix.index.cargo", actual);
    }

    @Test
    void testEntryIndexPrefixShouldUseLeadingZeroesWhenCalledWithPositiveNumber() {
        //given

        //when
        final var actual = BarjCargoUtil.entryIndexPrefix(1);

        //then
        Assertions.assertEquals("00000001", actual);
    }

    @Test
    void testEntryIndexPrefixShouldThrowExceptionWhenCalledWithNegativeNumber() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BarjCargoUtil.entryIndexPrefix(-1));

        //then + exception
    }
}
