package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.crypto.EncryptionKeyUtil;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

class BackupIncrementManifestTest {
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final long ORIGINAL_SIZE_BYTES = 1024L;
    private static final int CHUNK_SIZE_MEBIBYTE = 1024;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final ArchivedFileMetadata archive = ArchivedFileMetadata.builder()
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
        final FileMetadata file = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", ".path.txt").toAbsolutePath())
                .archiveMetadataId(archive.getId())
                .fileType(FileType.REGULAR_FILE)
                .owner("owner")
                .group("group")
                .posixPermissions("rwxr-xr-x")
                .originalSizeBytes(ORIGINAL_SIZE_BYTES)
                .lastModifiedUtcEpochSeconds(Instant.now().getEpochSecond())
                .originalChecksum("checksum")
                .hidden(true)
                .status(Change.NEW)
                .error("error")
                .build();
        final SecretKey secretKey = EncryptionKeyUtil.generateAesKey();
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();
        final byte[] encrypted = EncryptionKeyUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final BackupJobConfiguration config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .encryptionKey(keyPair.getPublic())
                .chunkSizeMebibyte(CHUNK_SIZE_MEBIBYTE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final BackupIncrementManifest expected = BackupIncrementManifest.builder()
                .versions(new TreeSet<>(Set.of(0)))
                .backupType(BackupType.FULL)
                .encryptionKey(encrypted)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-")
                .configuration(config)
                .archivedEntries(Map.of(archive.getId(), archive))
                .files(Map.of(file.getId(), file))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupIncrementManifest actual = objectMapper.readerFor(BackupIncrementManifest.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final FileMetadata file = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", "missing.md").toAbsolutePath())
                .fileType(FileType.SYMBOLIC_LINK)
                .status(Change.DELETED)
                .build();
        final BackupJobConfiguration config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.NONE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final BackupIncrementManifest expected = BackupIncrementManifest.builder()
                .versions(new TreeSet<>(Set.of(0, 1, 2)))
                .backupType(BackupType.FULL)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-consolidation-")
                .configuration(config)
                .files(Map.of(file.getId(), file))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupIncrementManifest actual = objectMapper.readerFor(BackupIncrementManifest.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDataEncryptionKeyShouldReturnDecryptedKeyWhenCalledWithTheRequiredPrivateKey() {
        //given
        final SecretKey secretKey = EncryptionKeyUtil.generateAesKey();
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();
        final byte[] encrypted = EncryptionKeyUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final BackupJobConfiguration config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .encryptionKey(keyPair.getPublic())
                .chunkSizeMebibyte(CHUNK_SIZE_MEBIBYTE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final BackupIncrementManifest underTest = BackupIncrementManifest.builder()
                .versions(new TreeSet<>(Set.of(0)))
                .backupType(BackupType.FULL)
                .encryptionKey(encrypted)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-")
                .configuration(config)
                .build();

        //when
        final SecretKey actual = underTest.dataEncryptionKey(keyPair.getPrivate());

        //then
        Assertions.assertEquals(secretKey, actual);
    }

    @Test
    void testGenerateDataEncryptionKeyShouldReturnDecryptedKeyWhenCalledWithTheRequiredPublicKey() {
        //given
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();
        final BackupJobConfiguration config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .checksumAlgorithm(HashAlgorithm.SHA256)
                .encryptionKey(keyPair.getPublic())
                .chunkSizeMebibyte(CHUNK_SIZE_MEBIBYTE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final BackupIncrementManifest underTest = BackupIncrementManifest.builder()
                .versions(new TreeSet<>(Set.of(0)))
                .backupType(BackupType.FULL)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-")
                .configuration(config)
                .build();

        //when
        final SecretKey actual = underTest.generateDataEncryptionKey(keyPair.getPublic());

        //then
        final SecretKey expected = underTest.dataEncryptionKey(keyPair.getPrivate());
        Assertions.assertEquals(expected, actual);
    }
}
