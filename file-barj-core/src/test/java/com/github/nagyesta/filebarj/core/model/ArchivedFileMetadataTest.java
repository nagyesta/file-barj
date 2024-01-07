package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

class ArchivedFileMetadataTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final var expected = ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .originalHash("hash")
                .archivedHash("archived")
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(1)
                        .entryName(UUID.randomUUID())
                        .build())
                .files(Set.of(UUID.randomUUID()))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final ArchivedFileMetadata actual = objectMapper.readerFor(ArchivedFileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final var expected = ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(1)
                        .entryName(UUID.randomUUID())
                        .build())
                .files(Set.of(UUID.randomUUID()))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final ArchivedFileMetadata actual = objectMapper.readerFor(ArchivedFileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testCopyArchiveDetailsShouldCopyEveryValueExceptFilesWhenCalled() {
        //given
        final var underTest = ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .originalHash("hash")
                .archivedHash("archived")
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(1)
                        .entryName(UUID.randomUUID())
                        .build())
                .files(Set.of(UUID.randomUUID()))
                .build();

        //when
        final var actual = underTest.copyArchiveDetails();

        //then
        Assertions.assertNotEquals(underTest.getId(), actual.getId());
        Assertions.assertEquals(underTest.getOriginalHash(), actual.getOriginalHash());
        Assertions.assertEquals(underTest.getArchivedHash(), actual.getArchivedHash());
        Assertions.assertEquals(underTest.getArchiveLocation(), actual.getArchiveLocation());
        Assertions.assertEquals(Set.of(), actual.getFiles());
    }
}
