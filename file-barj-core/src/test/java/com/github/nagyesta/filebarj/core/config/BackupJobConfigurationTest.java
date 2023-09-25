package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.crypto.EncryptionKeyUtil;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

class BackupJobConfigurationTest {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final BackupJobConfiguration expected = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .encryptionKey(EncryptionKeyUtil.generateRsaKeyPair().getPublic())
                .chunkSizeMebibyte(1024)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupJobConfiguration actual = objectMapper.readerFor(BackupJobConfiguration.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
        Assertions.assertEquals(expected.getChunkSizeMebibyte(), actual.getChunkSizeMebibyte());
        Assertions.assertIterableEquals(expected.getSources(), actual.getSources());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final BackupJobConfiguration expected = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.NONE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupJobConfiguration actual = objectMapper.readerFor(BackupJobConfiguration.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
        Assertions.assertEquals(expected.getChunkSizeMebibyte(), actual.getChunkSizeMebibyte());
        Assertions.assertIterableEquals(expected.getSources(), actual.getSources());
    }
}
