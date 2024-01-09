package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

class BackupJobConfigurationTest extends TempFileAwareTest {

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var expected = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.GZIP)
                .encryptionKey(EncryptionUtil.generateRsaKeyPair().getPublic())
                .chunkSizeMebibyte(1024)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(testRoot, "visible-file1.txt")).build()))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupJobConfiguration actual = objectMapper.readerFor(BackupJobConfiguration.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.getBackupType(), actual.getBackupType());
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
        Assertions.assertEquals(expected.getChunkSizeMebibyte(), actual.getChunkSizeMebibyte());
        Assertions.assertIterableEquals(expected.getSources(), actual.getSources());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var expected = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(testRoot, "visible-file1.txt")).build()))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupJobConfiguration actual = objectMapper.readerFor(BackupJobConfiguration.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.getBackupType(), actual.getBackupType());
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
        Assertions.assertEquals(expected.getChunkSizeMebibyte(), actual.getChunkSizeMebibyte());
        Assertions.assertIterableEquals(expected.getSources(), actual.getSources());
    }
}
