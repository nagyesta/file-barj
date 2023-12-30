package com.github.nagyesta.filebarj.io.stream.model;

import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class BarjCargoArchiveEntryTest {

    @Test
    void testGetFileContentShouldThrowExceptionWhenFileTypeIsNotRegularFile() {
        //given
        final var source = mock(BarjCargoArchiveFileInputStreamSource.class);
        final var underTest = new RandomAccessBarjCargoArchiveEntry(source, BarjCargoEntityIndex.builder()
                .path("path")
                .metadata(BarjCargoEntryBoundaries.builder()
                        .startChunkName("chunkName")
                        .endChunkName("chunkName")
                        .build())
                .fileType(FileType.SYMBOLIC_LINK)
                .build());

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.getFileContent(null));

        //then + exception
    }

    @Test
    void testGetLinkTargetShouldThrowExceptionWhenFileTypeIsNotSymbolicLink() {
        //given
        final var source = mock(BarjCargoArchiveFileInputStreamSource.class);
        final var underTest = new RandomAccessBarjCargoArchiveEntry(source, BarjCargoEntityIndex.builder()
                .path("path")
                .metadata(BarjCargoEntryBoundaries.builder()
                        .startChunkName("chunkName")
                        .endChunkName("chunkName")
                        .build())
                .fileType(FileType.REGULAR_FILE)
                .build());

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.getLinkTarget(null));

        //then + exception
    }

    @Test
    void getMetadata() {
    }
}
