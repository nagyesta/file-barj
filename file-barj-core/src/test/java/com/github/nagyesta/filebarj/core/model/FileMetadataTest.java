package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

class FileMetadataTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final FileMetadata expected = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", ".path.txt").toAbsolutePath())
                .archiveMetadataId(UUID.randomUUID())
                .fileType(FileType.REGULAR_FILE)
                .owner("owner")
                .group("group")
                .posixPermissions("rwxr-xr-x")
                .originalSizeBytes(1024L)
                .lastModifiedUtcEpochSeconds(123L)
                .originalChecksum("checksum")
                .hidden(true)
                .status(Change.NEW)
                .error("error")
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final FileMetadata actual = objectMapper.readerFor(FileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final FileMetadata expected = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", "missing.md").toAbsolutePath())
                .fileType(FileType.SYMBOLIC_LINK)
                .status(Change.DELETED)
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final FileMetadata actual = objectMapper.readerFor(FileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }
}
