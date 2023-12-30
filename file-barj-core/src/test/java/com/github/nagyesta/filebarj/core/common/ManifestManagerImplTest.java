package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.ValidationRules;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

class ManifestManagerImplTest extends TempFileAwareTest {
    public static final int A_SECOND = 1000;
    private final BackupJobConfiguration configuration = BackupJobConfiguration.builder()
            .fileNamePrefix("prefix")
            .sources(Set.of())
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
        final var underTest = new ManifestManagerImpl();

        //when
        final var actual = underTest.generateManifest(configuration, BackupType.FULL, 0);

        //then
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(configuration, actual.getConfiguration());
        Assertions.assertEquals(BackupType.FULL, actual.getBackupType());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGenerateManifestShouldThrowExceptionWhenCalledWithNullConfiguration() {
        //given
        final var underTest = new ManifestManagerImpl();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.generateManifest(null, BackupType.FULL, 0));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGenerateManifestShouldThrowExceptionWhenCalledWithNullBackupType() {
        //given
        final var underTest = new ManifestManagerImpl();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.generateManifest(configuration, null, 0));

        //then + exception
    }

    @Test
    void testLoadShouldReadPreviouslyPersistedManifestWhenUsingEncryption() throws IOException {
        //given
        final var underTest = new ManifestManagerImpl();
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.FULL)
                .encryptionKey(keyPair.getPublic())
                .build();
        final var expected = underTest.generateManifest(config, BackupType.FULL, 0);

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

    @Test
    void testLoadShouldReadPreviouslyPersistedManifestWhenNotUsingEncryption() throws IOException {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.FULL)
                .build();
        final var expected = underTest.generateManifest(config, BackupType.FULL, 0);

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

    @Test
    void testLoadShouldFilterOutManifestsAfterThresholdWhenATimeStampIsProvided()
            throws IOException, InterruptedException {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.FULL)
                .build();
        final var expected = underTest.generateManifest(config, BackupType.FULL, 0);
        underTest.persist(expected);
        Thread.sleep(A_SECOND);
        final var limit = Instant.now().getEpochSecond();
        Thread.sleep(A_SECOND);
        final var ignored = underTest.generateManifest(config, BackupType.FULL, 0);
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

    @Test
    void testLoadShouldFilterOutManifestsBeforeLatestFullBackupWhenMultipleFullBackupsAreEligible()
            throws IOException, InterruptedException {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.FULL)
                .build();
        final var ignored = underTest.generateManifest(config, BackupType.FULL, 0);
        underTest.persist(ignored);
        Thread.sleep(A_SECOND);
        final var expected = underTest.generateManifest(config, BackupType.FULL, 0);
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

    @Test
    void testLoadShouldThrowExceptionWhenAPreviousVersionIsMissing() throws InterruptedException {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.INCREMENTAL)
                .build();
        final var original = underTest.generateManifest(config, BackupType.FULL, 0);
        Thread.sleep(A_SECOND);
        final var secondIncrement = underTest.generateManifest(config, BackupType.INCREMENTAL, 2);
        underTest.persist(original);
        underTest.persist(secondIncrement);

        //when
        Assertions.assertThrows(ArchivalException.class,
                () -> underTest.load(destinationDirectory, "prefix", null, Long.MAX_VALUE));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testPersistShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = new ManifestManagerImpl();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.persist(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testLoadShouldThrowExceptionWhenCalledWithNullDirectory() {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.FULL)
                .build();
        final var manifest = underTest.generateManifest(config, BackupType.FULL, 0);
        underTest.persist(manifest);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.load(null, manifest.getFileNamePrefix(), null, Long.MAX_VALUE));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testLoadShouldThrowExceptionWhenCalledWithNullPrefix() {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.FULL)
                .build();
        final var manifest = underTest.generateManifest(config, BackupType.FULL, 0);
        underTest.persist(manifest);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.load(destinationDirectory, null, null, Long.MAX_VALUE));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidateShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        final var underTest = new ManifestManagerImpl();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.validate(null, ValidationRules.Persisted.class));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidateShouldThrowExceptionWhenCalledWithNullRules() {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(Path.of("/tmp")).build()))
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

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMergeForRestoreShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = new ManifestManagerImpl();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.mergeForRestore(null));

        //then + exception
    }
}
