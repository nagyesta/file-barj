package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.json.BackupIncrementMetadataReader;
import com.github.nagyesta.filebarj.core.json.BackupIncrementMetadataWriter;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.util.OsUtil;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final JsonFactory jsonFactory = new JsonFactory();

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws IOException {
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
                .absolutePath(BackupPath.of(Path.of("test"), "file", ".path.txt"))
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
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
            final var archivedFileMetadataSetRepository = dataStore.archivedFileMetadataSetRepository();
            final var files = fileMetadataSetRepository.createFileSet();
            final var archives = archivedFileMetadataSetRepository.createFileSet();
            fileMetadataSetRepository.appendTo(files, file);
            archivedFileMetadataSetRepository.appendTo(archives, archive);
            final var expected = BackupIncrementManifest.builder()
                    .appVersion(new AppVersion(0, 0, 1))
                    .versions(new TreeSet<>(Set.of(0)))
                    .backupType(BackupType.FULL)
                    .operatingSystem(OsUtil.getRawOsName())
                    .encryptionKeys(Map.of(0, Map.of(0, dek)))
                    .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                    .fileNamePrefix("backup-")
                    .configuration(config)
                    .archivedEntries(archives)
                    .dataStore(dataStore)
                    .files(files)
                    .dataFileNames(List.of("file1", "file2"))
                    .build();
            final var stringWriter = new StringBuilderWriter();
            final var generator = jsonFactory.createGenerator(stringWriter);
            final var jsonWriter = new BackupIncrementMetadataWriter(generator, objectMapper);
            jsonWriter.write(expected);
            final var json = stringWriter.toString();

            //when
            final var parser = jsonFactory.createParser(new StringReader(json));
            final var jsonReader = new BackupIncrementMetadataReader(dataStore, parser, objectMapper);
            final var actual = jsonReader.read();

            //then
            Assertions.assertEquals(expected, actual);
            Assertions.assertEquals(expected.hashCode(), actual.hashCode());
            assertFilesAreMatching(dataStore, expected, actual);
        }
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws IOException {
        //given
        final var file = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(BackupPath.of(Path.of("test"), "file", "missing.md"))
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
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(Path.of(TEMP_DIR), "visible-file1.txt")).build()))
                .build();
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
            final var archivedFileMetadataSetRepository = dataStore.archivedFileMetadataSetRepository();
            final var files = fileMetadataSetRepository.createFileSet();
            final var archives = archivedFileMetadataSetRepository.createFileSet();
            fileMetadataSetRepository.appendTo(files, file);
            final var expected = BackupIncrementManifest.builder()
                    .appVersion(new AppVersion(0, 0, 1))
                    .versions(new TreeSet<>(Set.of(0, 1, 2)))
                    .backupType(BackupType.FULL)
                    .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                    .fileNamePrefix("backup-consolidation-")
                    .configuration(config)
                    .dataStore(dataStore)
                    .files(files)
                    .archivedEntries(archives)
                    .dataFileNames(List.of("file1", "file2"))
                    .build();
            final var stringWriter = new StringBuilderWriter();
            final var generator = jsonFactory.createGenerator(stringWriter);
            final var jsonWriter = new BackupIncrementMetadataWriter(generator, objectMapper);
            jsonWriter.write(expected);
            final var json = stringWriter.toString();

            //when
            final var parser = jsonFactory.createParser(new StringReader(json));
            final var jsonReader = new BackupIncrementMetadataReader(dataStore, parser, objectMapper);
            final var actual = jsonReader.read();

            //then
            Assertions.assertEquals(expected, actual);
            Assertions.assertEquals(expected.hashCode(), actual.hashCode());
            assertFilesAreMatching(dataStore, expected, actual);
        }
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
        final var entryLocator = new ArchiveEntryLocator(0, UUIDS.get(0));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.dataDecryptionKey(null, entryLocator));
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
        final var aPrivate = keyPair.getPrivate();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.dataDecryptionKey(aPrivate, null));

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
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(Path.of(TEMP_DIR), "visible-file1.txt")).build()))
                .build();
    }

    private static BackupIncrementManifest getUnderTest(
            final String dek,
            final BackupJobConfiguration config) {
        final var builder = BackupIncrementManifest.builder()
                .dataStore(DataStore.newInMemoryInstance())
                .appVersion(new AppVersion(0, 0, 1))
                .versions(new TreeSet<>(Set.of(0)))
                .backupType(BackupType.FULL)
                .startTimeUtcEpochSeconds(Instant.now().getEpochSecond())
                .fileNamePrefix("backup-")
                .configuration(config);
        Optional.ofNullable(dek).ifPresent(key -> builder.encryptionKeys(Map.of(0, Map.of(0, key))));
        return builder.build();
    }

    private static void assertFilesAreMatching(
            final DataStore dataStore,
            final BackupIncrementManifest expected,
            final BackupIncrementManifest actual) {
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore.archivedFileMetadataSetRepository();
        final var actualFileMap = fileMetadataSetRepository.findAll(actual.getFiles(), 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity()));
        final var actualArchiveMap = archivedFileMetadataSetRepository.findAll(actual.getArchivedEntries(), 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(ArchivedFileMetadata::getId, Function.identity()));
        final var expectedFileMap = fileMetadataSetRepository.findAll(expected.getFiles(), 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity()));
        final var expectedArchiveMap = archivedFileMetadataSetRepository.findAll(expected.getArchivedEntries(), 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(ArchivedFileMetadata::getId, Function.identity()));
        Assertions.assertIterableEquals(expectedFileMap.entrySet(), actualFileMap.entrySet());
        Assertions.assertIterableEquals(expectedArchiveMap.entrySet(), actualArchiveMap.entrySet());
    }
}
