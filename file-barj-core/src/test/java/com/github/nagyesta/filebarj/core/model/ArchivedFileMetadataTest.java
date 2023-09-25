package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.crypto.EncryptionKeyUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

class ArchivedFileMetadataTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final ArchivedFileMetadata expected = ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .originalChecksum("checksum")
                .archivedChecksum("archived")
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(1)
                        .entryName(UUID.randomUUID())
                        .randomBytes(EncryptionKeyUtil.generateSecureRandomBytes())
                        .build())
                .files(Set.of(UUID.randomUUID()))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final ArchivedFileMetadata actual = objectMapper.readerFor(ArchivedFileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final ArchivedFileMetadata expected = ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(1)
                        .entryName(UUID.randomUUID())
                        .build())
                .files(Set.of(UUID.randomUUID()))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final ArchivedFileMetadata actual = objectMapper.readerFor(ArchivedFileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }
}
