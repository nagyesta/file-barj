package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.ValidationRules;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.progress.NoOpProgressTracker;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;

class ManifestManagerImplTest extends TempFileAwareTest {

    private final BackupJobConfiguration configuration = BackupJobConfiguration.builder()
            .fileNamePrefix("prefix")
            .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
            .compression(CompressionAlgorithm.NONE)
            .hashAlgorithm(HashAlgorithm.SHA256)
            .chunkSizeMebibyte(1)
            .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
            .destinationDirectory(Path.of("/tmp"))
            .backupType(BackupType.INCREMENTAL)
            .build();

    @Test
    void testGenerateManifestShouldAllowOverridingTheBackupTypeWhenCalledWithFullBackupOfIncrementalConfiguration() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            final var actual = underTest.generateManifest(configuration, BackupType.FULL, 0);

            //then
            Assertions.assertNotNull(actual);
            Assertions.assertEquals(configuration, actual.getConfiguration());
            Assertions.assertEquals(BackupType.FULL, actual.getBackupType());
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGenerateManifestShouldThrowExceptionWhenCalledWithNullConfiguration() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.generateManifest(null, BackupType.FULL, 0));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGenerateManifestShouldThrowExceptionWhenCalledWithNullBackupType() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.generateManifest(configuration, null, 0));

            //then + exception
        }
    }

    @Test
    void testLoadShouldReadPreviouslyPersistedManifestWhenUsingEncryption() throws IOException {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var keyPair = EncryptionUtil.generateRsaKeyPair();
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .encryptionKey(keyPair.getPublic())
                    .build();
            final var expected = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(expected);

            //when
            underTest.persist(expected);
            final var actual = underTest.load(destinationDirectory, "prefix", keyPair.getPrivate(), Long.MAX_VALUE);

            //then
            Assertions.assertTrue(destinationDirectory.toFile().exists());
            final var historyPath = destinationDirectory.resolve(".history/" + expected.getFileNamePrefix() + ".manifest.json.gz");
            Assertions.assertTrue(historyPath.toFile().exists());
            final var encryptedPath = destinationDirectory.resolve(expected.getFileNamePrefix() + ".manifest.cargo");
            Assertions.assertTrue(encryptedPath.toFile().exists());
            Assertions.assertEquals(1, actual.size());
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertNotEquals(Files.size(historyPath), Files.size(encryptedPath));
        }
    }

    @Test
    void testLoadShouldReadPreviouslyPersistedManifestWhenNotUsingEncryption() throws IOException {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var expected = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(expected);

            //when
            underTest.persist(expected);
            final var actual = underTest.load(destinationDirectory, "prefix", null, Long.MAX_VALUE);

            //then
            Assertions.assertTrue(destinationDirectory.toFile().exists());
            final var historyPath = destinationDirectory.resolve(".history/" + expected.getFileNamePrefix() + ".manifest.json.gz");
            Assertions.assertTrue(historyPath.toFile().exists());
            final var encryptedPath = destinationDirectory.resolve(expected.getFileNamePrefix() + ".manifest.cargo");
            Assertions.assertTrue(encryptedPath.toFile().exists());
            Assertions.assertEquals(1, actual.size());
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertEquals(Files.size(historyPath), Files.size(encryptedPath));
        }
    }

    @Test
    @SuppressWarnings("java:S2925")
    void testLoadShouldFilterOutManifestsAfterThresholdWhenATimeStampIsProvided()
            throws IOException, InterruptedException {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot)).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var expected = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(expected);
            underTest.persist(expected);
            Thread.sleep(Duration.ofSeconds(1).toMillis());
            final var limit = Instant.now().getEpochSecond();
            Thread.sleep(Duration.ofSeconds(1).toMillis());
            final var ignored = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(ignored);
            underTest.persist(ignored);

            //when
            final var actual = underTest.load(destinationDirectory, "prefix", null, limit);

            //then
            Assertions.assertTrue(destinationDirectory.toFile().exists());
            final var historyPath = destinationDirectory.resolve(".history/" + expected.getFileNamePrefix() + ".manifest.json.gz");
            Assertions.assertTrue(historyPath.toFile().exists());
            final var encryptedPath = destinationDirectory.resolve(expected.getFileNamePrefix() + ".manifest.cargo");
            Assertions.assertTrue(encryptedPath.toFile().exists());
            Assertions.assertEquals(1, actual.size());
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertEquals(Files.size(historyPath), Files.size(encryptedPath));
        }
    }

    @Test
    @SuppressWarnings("java:S2925")
    void testLoadShouldFilterOutManifestsBeforeLatestFullBackupWhenMultipleFullBackupsAreEligible()
            throws IOException, InterruptedException {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var ignored = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(ignored);
            underTest.persist(ignored);
            Thread.sleep(Duration.ofSeconds(1).toMillis());
            final var expected = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(expected);
            underTest.persist(expected);

            //when
            final var actual = underTest.load(destinationDirectory, "prefix", null, Long.MAX_VALUE);

            //then
            Assertions.assertTrue(destinationDirectory.toFile().exists());
            final var historyPath = destinationDirectory.resolve(".history/" + expected.getFileNamePrefix() + ".manifest.json.gz");
            Assertions.assertTrue(historyPath.toFile().exists());
            final var encryptedPath = destinationDirectory.resolve(expected.getFileNamePrefix() + ".manifest.cargo");
            Assertions.assertTrue(encryptedPath.toFile().exists());
            Assertions.assertEquals(1, actual.size());
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertEquals(Files.size(historyPath), Files.size(encryptedPath));
        }
    }

    @Test
    @SuppressWarnings("java:S2925")
    void testLoadShouldThrowExceptionWhenAPreviousVersionIsMissing() throws InterruptedException {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.INCREMENTAL)
                    .build();
            final var original = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(original);
            Thread.sleep(Duration.ofSeconds(1).toMillis());
            final var secondIncrement = underTest.generateManifest(config, BackupType.INCREMENTAL, 2);
            simulateThatADirectoryWasArchived(secondIncrement);
            underTest.persist(original);
            underTest.persist(secondIncrement);

            //when
            Assertions.assertThrows(ArchivalException.class,
                    () -> underTest.load(destinationDirectory, "prefix", null, Long.MAX_VALUE));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testPersistShouldThrowExceptionWhenCalledWithNull() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.persist(null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testPersistShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destination = Path.of("destination");

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.persist(null, destination));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testPersistShouldThrowExceptionWhenCalledWithNullDestination() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.persist(mock(BackupIncrementManifest.class), null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testLoadShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var manifest = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(manifest);
            underTest.persist(manifest);
            final var fileNamePrefix = manifest.getFileNamePrefix();

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.load(null, fileNamePrefix, null, Long.MAX_VALUE));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testLoadShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var manifest = underTest.generateManifest(config, BackupType.FULL, 0);
            simulateThatADirectoryWasArchived(manifest);
            underTest.persist(manifest);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.load(destinationDirectory, null, null, Long.MAX_VALUE));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidateShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.validate(null, ValidationRules.Persisted.class));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidateShouldThrowExceptionWhenCalledWithNullRules() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var manifest = underTest.generateManifest(config, BackupType.FULL, 0);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.validate(manifest, null));

            //then + exception
        }
    }

    @Test
    void testValidateShouldThrowExceptionWhenCalledWithInvalidData() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destinationDirectory = testDataRoot.resolve("destination");
            final var config = BackupJobConfiguration.builder()
                    .fileNamePrefix("prefix")
                    .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("/tmp")).build()))
                    .compression(CompressionAlgorithm.GZIP)
                    .hashAlgorithm(HashAlgorithm.SHA256)
                    .chunkSizeMebibyte(1)
                    .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                    .destinationDirectory(destinationDirectory)
                    .backupType(BackupType.FULL)
                    .build();
            final var manifest = underTest.generateManifest(config, BackupType.FULL, 0);

            //when
            Assertions.assertThrows(ValidationException.class,
                    () -> underTest.validate(manifest, ValidationRules.Persisted.class));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMergeForRestoreShouldThrowExceptionWhenCalledWithNull() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.mergeForRestore(null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDeleteIncrementShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var destination = Path.of("destination");

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.deleteIncrement(destination, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDeleteIncrementShouldThrowExceptionWhenCalledWithNullDestination() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.deleteIncrement(null, mock(BackupIncrementManifest.class)));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testLoadPreviousManifestsForBackupShouldThrowExceptionWhenCalledWithNull() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {
            final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.loadPreviousManifestsForBackup(null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullProgressTracker() {
        //given
        try (var dataStore = DataStore.newInMemoryInstance()) {

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> new ManifestManagerImpl(dataStore, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDataStore() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ManifestManagerImpl(null, NoOpProgressTracker.INSTANCE));

        //then + exception
    }

    private void simulateThatADirectoryWasArchived(final BackupIncrementManifest expected) {
        expected.setIndexFileName("index");
        expected.setDataFileNames(List.of("data"));
        final var directoryMetadata = FileMetadataParserFactory.newInstance().parse(testDataRoot.toFile(), configuration);
        expected.getFiles().put(directoryMetadata.getId(), directoryMetadata);
    }
}
