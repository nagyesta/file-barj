package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;

import static com.github.nagyesta.filebarj.core.model.BackupIncrementManifest.DEK_COUNT;

class BackupIncrementManifestTest {
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final long ORIGINAL_SIZE_BYTES = 1024L;
    private static final int CHUNK_SIZE_MEBIBYTE = 1024;
    private static final List<UUID> UUIDS = List.of(
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b740"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b74f"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b74e"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b74d"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b74c"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b74b"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b74a"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b749"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b748"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b747"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b746"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b745"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b744"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b743"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b742"),
            UUID.fromString("c516820c-53cb-42a0-ab8a-2775b019b741")
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final var archive = ArchivedFileMetadata.builder()
                .id(UUID.randomUUID())
                .originalHash("hash")
                .archivedHash("archived")
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(1)
                        .entryName(UUID.randomUUID())
                        .build())
                .files(Set.of(UUID.randomUUID()))
                .build();
        final var file = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", ".path.txt").toAbsolutePath())
                .archiveMetadataId(archive.getId())
                .fileType(FileType.REGULAR_FILE)
                .owner("owner")
                .group("group")
                .posixPermissions("rwxr-xr-x")
                .originalSizeBytes(ORIGINAL_SIZE_BYTES)
                .lastModifiedUtcEpochSeconds(Instant.now().getEpochSecond())
                .originalHash("hash")
                .hidden(true)
                .status(Change.NEW)
                .error("error")
                .build();
        final var secretKey = EncryptionUtil.generateAesKey();
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var encrypted = EncryptionUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final var config = getConfiguration(keyPair);
        final var dek = Base64.getEncoder().encodeToString(encrypted);
        final var expected = BackupIncrementManifest.builder()
                .appVersion(new AppVersion(0, 0, 1))
                .versions(new TreeSet<>(Set.of(0)))
                .backupType(BackupType.FULL)
                .encryptionKeys(Map.of(0, Map.of(0, dek)))
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-")
                .configuration(config)
                .archivedEntries(Map.of(archive.getId(), archive))
                .files(Map.of(file.getId(), file))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupIncrementManifest actual = objectMapper.readerFor(BackupIncrementManifest.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final var file = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", "missing.md").toAbsolutePath())
                .fileType(FileType.SYMBOLIC_LINK)
                .status(Change.DELETED)
                .build();
        final var config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
        final var expected = BackupIncrementManifest.builder()
                .appVersion(new AppVersion(0, 0, 1))
                .versions(new TreeSet<>(Set.of(0, 1, 2)))
                .backupType(BackupType.FULL)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-consolidation-")
                .configuration(config)
                .files(Map.of(file.getId(), file))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupIncrementManifest actual = objectMapper.readerFor(BackupIncrementManifest.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDataDecryptionKeyShouldReturnDecryptedKeyWhenCalledWithTheRequiredPrivateKey() {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var encrypted = EncryptionUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final var config = getConfiguration(keyPair);
        final var dek = Base64.getEncoder().encodeToString(encrypted);
        final var underTest = getUnderTest(dek, config);

        //when
        final var actual = underTest.dataDecryptionKey(keyPair.getPrivate(), new ArchiveEntryLocator(0, UUIDS.get(0)));
        final var actualIndex = underTest.dataIndexDecryptionKey(keyPair.getPrivate(), underTest.getVersions().first());

        //then
        Assertions.assertEquals(secretKey, actual);
        Assertions.assertEquals(secretKey, actualIndex);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDataDecryptionKeyShouldThrowExceptionWhenCalledWithNullPrivateKey() {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var encrypted = EncryptionUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final var config = getConfiguration(keyPair);
        final var dek = Base64.getEncoder().encodeToString(encrypted);
        final var underTest = getUnderTest(dek, config);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.dataDecryptionKey(null, new ArchiveEntryLocator(0, UUIDS.get(0))));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.dataIndexDecryptionKey(null, 0));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDataDecryptionKeyShouldThrowExceptionWhenCalledWithNullEntityName() {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var encrypted = EncryptionUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final var config = getConfiguration(keyPair);
        final var dek = Base64.getEncoder().encodeToString(encrypted);
        final var underTest = getUnderTest(dek, config);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.dataDecryptionKey(keyPair.getPrivate(), null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDataEncryptionKeyShouldThrowExceptionWhenCalledWithNullEntityName() {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var encrypted = EncryptionUtil.encryptBytes(keyPair.getPublic(), secretKey.getEncoded());
        final var config = getConfiguration(keyPair);
        final var dek = Base64.getEncoder().encodeToString(encrypted);
        final var underTest = getUnderTest(dek, config);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.dataEncryptionKey(null));

        //then + exception
    }

    @Test
    void testGenerateDataEncryptionKeysShouldReturnDecryptedKeyWhenCalledWithTheRequiredPublicKey() {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var config = getConfiguration(keyPair);
        final var underTest = getUnderTest(null, config);

        //when
        final var actual = underTest.generateDataEncryptionKeys(keyPair.getPublic());

        //then
        for (var i = 0; i < DEK_COUNT; i++) {
            final var expected = underTest.dataDecryptionKey(keyPair.getPrivate(), new ArchiveEntryLocator(0, UUIDS.get(i)));
            Assertions.assertEquals(expected, actual.get(i), "Secret keys don't match for index: " + i);
        }
        Assertions.assertEquals(underTest.dataIndexEncryptionKey(), actual.get(0), "Secret keys don't match for index encryption.");
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGenerateDataEncryptionKeysShouldThrowExceptionWhenCalledWithNullPublicKey() {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var config = getConfiguration(keyPair);
        final var underTest = getUnderTest(null, config);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.generateDataEncryptionKeys(null));

        //then + exception
    }

    private static BackupJobConfiguration getConfiguration(final KeyPair keyPair) {
        return BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.NONE)
                .encryptionKey(keyPair.getPublic())
                .chunkSizeMebibyte(CHUNK_SIZE_MEBIBYTE)
                .destinationDirectory(Path.of(TEMP_DIR, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(Path.of(TEMP_DIR, "visible-file1.txt")).build()))
                .build();
    }

    private static BackupIncrementManifest getUnderTest(final String dek, final BackupJobConfiguration config) {
        final var builder = BackupIncrementManifest.builder()
                .appVersion(new AppVersion(0, 0, 1))
                .versions(new TreeSet<>(Set.of(0)))
                .backupType(BackupType.FULL)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-")
                .configuration(config);
        Optional.ofNullable(dek).ifPresent(key -> builder.encryptionKeys(Map.of(0, Map.of(0, key))));
        return builder.build();
    }
}
