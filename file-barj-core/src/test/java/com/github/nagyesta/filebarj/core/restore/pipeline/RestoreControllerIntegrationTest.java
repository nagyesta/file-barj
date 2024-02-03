package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.config.*;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

class RestoreControllerIntegrationTest extends TempFileAwareTest {
    public static final int A_SECOND = 1000;

    @SuppressWarnings({"MagicNumber", "checkstyle:MagicNumber"})
    public static Stream<Arguments> restoreParameterProvider() {
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, null, 1, HashAlgorithm.SHA256, true))
                .add(Arguments.of(null, null, 2, HashAlgorithm.NONE, false))
                .add(Arguments.of(null, null, 500, HashAlgorithm.MD5, true))
                .add(Arguments.of(keyPair.getPublic(), keyPair.getPrivate(), 1, HashAlgorithm.NONE, false))
                .add(Arguments.of(keyPair.getPublic(), keyPair.getPrivate(), 2, HashAlgorithm.MD5, true))
                .add(Arguments.of(keyPair.getPublic(), keyPair.getPrivate(), 500, HashAlgorithm.SHA256, false))
                .build();
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithAPathWithoutBackups() {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backup = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, source, backup, null, HashAlgorithm.SHA256);

        //when
        Assertions.assertThrows(ArchivalException.class, () -> new RestoreController(
                configuration.getDestinationDirectory(), configuration.getFileNamePrefix(), null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testExecuteShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backup = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, source, backup, null, HashAlgorithm.SHA256);
        FileUtils.copyFile(getExampleResource(), source.resolve("A.png").toFile());
        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);
        final var underTest = new RestoreController(
                configuration.getDestinationDirectory(), configuration.getFileNamePrefix(), null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.execute(null));

        //then + exception
    }

    @Test
    void testExecuteShouldThrowExceptionWhenCalledWithLessThanOneThreads() throws IOException {
        //given
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backup = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restore = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, source, backup, null, HashAlgorithm.SHA256);
        FileUtils.copyFile(getExampleResource(), source.resolve("A.png").toFile());
        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        final var underTest = new RestoreController(
                configuration.getDestinationDirectory(), configuration.getFileNamePrefix(), null);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(backup, restore)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.execute(RestoreTask.builder()
                        .restoreTargets(restoreTargets)
                        .threads(0)
                        .dryRun(false)
                        .build()));

        //then + exception
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldRestoreFilesToDestinationWhenExecutedWithValidInput(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var movedBackupDir = testDataRoot.resolve("moved-backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, sourceDir, backupDir, encryptionKey, hash);

        final var sourceFiles = List.of(
                sourceDir.resolve("A.png"),
                sourceDir.resolve("B.png"),
                sourceDir.resolve("C.png"));
        for (final var sourceFile : sourceFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var sourceLinkInternal = sourceDir.resolve("folder/internal.png");
        final var internalLinkTarget = sourceDir.resolve("A.png");
        Files.createSymbolicLink(sourceLinkInternal, internalLinkTarget);
        final var sourceLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(sourceLinkExternal, externalLinkTarget);

        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        Files.move(backupDir, movedBackupDir);

        final var underTest = new RestoreController(
                movedBackupDir, configuration.getFileNamePrefix(), decryptionKey);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));

        //when
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(false)
                .build());

        //then
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        final var metadataParser = FileMetadataParserFactory.newInstance();
        for (final var sourceFile : sourceFiles) {
            final var restoredFile = realRestorePath.resolve(sourceFile.getFileName().toString());
            assertFileIsFullyRestored(sourceFile, restoredFile, metadataParser, configuration);
        }
        final var restoredInternal = Files.readSymbolicLink(realRestorePath.resolve("folder/internal.png")).toAbsolutePath();
        Assertions.assertEquals(sourceDir.relativize(internalLinkTarget), realRestorePath.relativize(restoredInternal));
        final var restoredExternal = Files.readSymbolicLink(realRestorePath.resolve("external.png")).toAbsolutePath();
        Assertions.assertEquals(externalLinkTarget, restoredExternal);
        assertFileMetadataMatches(sourceFolder, realRestorePath.resolve("folder"), metadataParser, configuration);
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldRestoreOnlyIncludedFilesToDestinationWhenExecutedWithIncludeFilter(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var movedBackupDir = testDataRoot.resolve("moved-backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, sourceDir, backupDir, encryptionKey, hash);

        final var aPng = sourceDir.resolve("A.png");
        final var bPng = sourceDir.resolve("B.png");
        final var cPng = sourceDir.resolve("C.png");
        final var sourceFiles = List.of(aPng, bPng, cPng);
        for (final var sourceFile : sourceFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var sourceLinkInternal = sourceDir.resolve("folder/internal.png");
        Files.createSymbolicLink(sourceLinkInternal, aPng);
        final var sourceLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(sourceLinkExternal, externalLinkTarget);

        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        Files.move(backupDir, movedBackupDir);

        final var underTest = new RestoreController(
                movedBackupDir, configuration.getFileNamePrefix(), decryptionKey);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        final var restoredAPng = realRestorePath.resolve(aPng.getFileName().toString());
        final var restoredBPng = realRestorePath.resolve(bPng.getFileName().toString());
        final var restoredCPng = realRestorePath.resolve(cPng.getFileName().toString());
        final var restoredFolder = realRestorePath.resolve("folder");
        final var restoredExternal = realRestorePath.resolve("external.png");

        final var metadataParser = FileMetadataParserFactory.newInstance();

        //when "A.png" is restored
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(false)
                .deleteFilesNotInBackup(true)
                .includedPath(aPng)
                .build());

        //then nothing else exists
        assertFileIsFullyRestored(aPng, restoredAPng, metadataParser, configuration);
        Assertions.assertTrue(Files.notExists(restoredBPng, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertTrue(Files.notExists(restoredCPng, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertTrue(Files.notExists(restoredFolder, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertTrue(Files.notExists(restoredExternal, LinkOption.NOFOLLOW_LINKS));

        //when the "folder" is restored
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(false)
                .deleteFilesNotInBackup(true)
                .includedPath(sourceDir.resolve("folder"))
                .build());

        //then both "A.png" and the full contents of the "folder" are restored
        assertFileIsFullyRestored(aPng, restoredAPng, metadataParser, configuration);
        Assertions.assertTrue(Files.notExists(restoredBPng, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertTrue(Files.notExists(restoredCPng, LinkOption.NOFOLLOW_LINKS));
        final var restoredInternal = Files.readSymbolicLink(realRestorePath.resolve("folder/internal.png")).toAbsolutePath();
        Assertions.assertEquals(sourceDir.relativize(aPng), realRestorePath.relativize(restoredInternal));
        Assertions.assertTrue(Files.notExists(restoredExternal, LinkOption.NOFOLLOW_LINKS));
        assertFileMetadataMatches(sourceFolder, restoredFolder, metadataParser, configuration);
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldRestoreFilesToDestinationWhenTargetFilesAlreadyExistWithDifferentContent(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, sourceDir, backupDir, encryptionKey, hash);

        final var sourceFiles = List.of(
                sourceDir.resolve("A.png"),
                sourceDir.resolve("B.png"),
                sourceDir.resolve("C.png"));
        for (final var sourceFile : sourceFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var sourceLinkInternal = sourceDir.resolve("folder/internal.png");
        final var internalLinkTarget = sourceDir.resolve("A.png");
        Files.createSymbolicLink(sourceLinkInternal, internalLinkTarget);
        final var sourceLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(sourceLinkExternal, externalLinkTarget);

        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        final var underTest = new RestoreController(
                backupDir, configuration.getFileNamePrefix(), decryptionKey);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        Files.createDirectories(realRestorePath);
        final var restoredA = realRestorePath.resolve("A.png");
        Files.createFile(restoredA);
        Files.createSymbolicLink(realRestorePath.resolve("B.png"), restoredA);
        Files.createSymbolicLink(realRestorePath.resolve("external.png"), restoredA);
        Files.createDirectories(realRestorePath.resolve("C.png"));
        Files.createDirectories(realRestorePath.resolve("C.png/D.png"));
        Files.createSymbolicLink(realRestorePath.resolve("folder"), restoredA);

        //when
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(false)
                .build());

        //then
        final var metadataParser = FileMetadataParserFactory.newInstance();
        for (final var sourceFile : sourceFiles) {
            final var restoredFile = realRestorePath.resolve(sourceFile.getFileName().toString());
            assertFileIsFullyRestored(sourceFile, restoredFile, metadataParser, configuration);
        }
        final var restoredInternal = Files.readSymbolicLink(realRestorePath.resolve("folder/internal.png")).toAbsolutePath();
        Assertions.assertEquals(sourceDir.relativize(internalLinkTarget), realRestorePath.relativize(restoredInternal));
        final var restoredExternal = Files.readSymbolicLink(realRestorePath.resolve("external.png")).toAbsolutePath();
        Assertions.assertEquals(externalLinkTarget, restoredExternal);
        assertFileMetadataMatches(sourceFolder, realRestorePath.resolve("folder"), metadataParser, configuration);
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldOnlySimulateRestoreWhenTargetFilesAlreadyExistWithDifferentContentAndDryRunIsUsed(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash, final boolean deleteLeftOver) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, sourceDir, backupDir, encryptionKey, hash);

        final var sourceFiles = List.of(
                sourceDir.resolve("A.png"),
                sourceDir.resolve("B.png"),
                sourceDir.resolve("C.png"));
        for (final var sourceFile : sourceFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var sourceLinkInternal = sourceDir.resolve("folder/internal.png");
        final var internalLinkTarget = sourceDir.resolve("A.png");
        Files.createSymbolicLink(sourceLinkInternal, internalLinkTarget);
        final var sourceLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(sourceLinkExternal, externalLinkTarget);

        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        final var underTest = new RestoreController(
                backupDir, configuration.getFileNamePrefix(), decryptionKey);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        Files.createDirectories(realRestorePath);
        final var restoredA = realRestorePath.resolve("A.png");
        final var restoredB = realRestorePath.resolve("B.png");
        final var restoredExternal = realRestorePath.resolve("external.png");
        final var restoredCD = realRestorePath.resolve("C.png/D.png");
        final var restoredFolder = realRestorePath.resolve("folder");
        Files.createFile(restoredA);
        Files.createSymbolicLink(restoredB, restoredA);
        Files.createSymbolicLink(restoredExternal, restoredA);
        Files.createDirectories(restoredCD);
        Files.createSymbolicLink(restoredFolder, restoredA);

        //when
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(true)
                .deleteFilesNotInBackup(deleteLeftOver)
                .build());

        //then
        Assertions.assertTrue(restoredA.toFile().exists());
        Assertions.assertEquals(0, restoredA.toFile().length());
        Assertions.assertEquals(restoredA, Files.readSymbolicLink(restoredB));
        Assertions.assertEquals(restoredA, Files.readSymbolicLink(restoredExternal));
        Assertions.assertEquals(restoredA, Files.readSymbolicLink(restoredFolder));
        Assertions.assertTrue(restoredCD.toFile().exists());
        Assertions.assertTrue(restoredCD.toFile().isDirectory());
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldRestoreFilesToDestinationWhenTargetFilesAlreadyExistWithPartiallyMatchingContent(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, sourceDir, backupDir, encryptionKey, hash);

        final var sourceFiles = List.of(
                sourceDir.resolve("A.png"),
                sourceDir.resolve("B.png"),
                sourceDir.resolve("C.png"));
        for (final var sourceFile : sourceFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var sourceLinkInternal = sourceDir.resolve("folder/internal.png");
        final var internalLinkTarget = sourceDir.resolve("A.png");
        Files.createSymbolicLink(sourceLinkInternal, internalLinkTarget);
        final var sourceLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(sourceLinkExternal, externalLinkTarget);

        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        final var underTest = new RestoreController(
                backupDir, configuration.getFileNamePrefix(), decryptionKey);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        Files.createDirectories(realRestorePath);
        Files.copy(sourceDir.resolve("A.png"), realRestorePath.resolve("A.png"));
        Files.copy(sourceDir.resolve("B.png"), realRestorePath.resolve("B.png"));
        Files.copy(sourceDir.resolve("C.png"), realRestorePath.resolve("C.png"));
        Files.createSymbolicLink(realRestorePath.resolve("external.png"), externalLinkTarget);

        //when
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(false)
                .build());

        //then
        final var metadataParser = FileMetadataParserFactory.newInstance();
        for (final var sourceFile : sourceFiles) {
            final var restoredFile = realRestorePath.resolve(sourceFile.getFileName().toString());
            assertFileIsFullyRestored(sourceFile, restoredFile, metadataParser, configuration);
        }
        final var restoredInternal = Files.readSymbolicLink(realRestorePath.resolve("folder/internal.png")).toAbsolutePath();
        Assertions.assertEquals(sourceDir.relativize(internalLinkTarget), realRestorePath.relativize(restoredInternal));
        final var restoredExternal = Files.readSymbolicLink(realRestorePath.resolve("external.png")).toAbsolutePath();
        Assertions.assertEquals(externalLinkTarget, restoredExternal);
        assertFileMetadataMatches(sourceFolder, realRestorePath.resolve("folder"), metadataParser, configuration);
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldRestoreFilesToDestinationWhenExecutedWithIncrementalBackup(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash, final boolean deleteLeftOver) throws IOException, InterruptedException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.INCREMENTAL, sourceDir, backupDir, encryptionKey, hash);

        final var originalFiles = List.of(
                sourceDir.resolve("unchanged-A.png"),
                sourceDir.resolve("unchanged-B.png"),
                sourceDir.resolve("unchanged-C.png"));
        for (final var sourceFile : originalFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var deleted = sourceDir.resolve("deleted.txt");
        Files.createFile(deleted);
        Files.writeString(deleted, "deleted content");
        final var changed = sourceDir.resolve("changed.txt");
        Files.createFile(changed);
        Files.writeString(changed, "original content");
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var changedLinkInternal = sourceDir.resolve("folder/changed.png");
        final var internalLinkTarget = sourceDir.resolve("unchanged-A.png");
        Files.createSymbolicLink(changedLinkInternal, internalLinkTarget);
        final var deletedLinkInternal = sourceDir.resolve("folder/deleted.png");
        Files.createSymbolicLink(deletedLinkInternal, internalLinkTarget);
        final var originalLinkInternal = sourceDir.resolve("folder/internal.png");
        Files.createSymbolicLink(originalLinkInternal, internalLinkTarget);
        final var originalLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(originalLinkExternal, externalLinkTarget);

        new BackupController(configuration, true).execute(1);

        Files.delete(deleted);
        final var expectedChangedContent = "changed content";
        Files.writeString(changed, expectedChangedContent);
        final var added = sourceDir.resolve("added.png");
        FileUtils.copyFile(getExampleResource(), added.toFile());

        final var alternativeLinkTarget = sourceDir.resolve("unchanged-B.png");
        Files.delete(changedLinkInternal);
        Files.createSymbolicLink(changedLinkInternal, alternativeLinkTarget);
        Files.delete(deletedLinkInternal);
        final var addedLinkExternal = sourceDir.resolve("folder/added-external.png");
        Files.createSymbolicLink(addedLinkExternal, externalLinkTarget);

        final var fullBackupTime = Instant.now().getEpochSecond();
        Thread.sleep(A_SECOND);
        new BackupController(configuration, false).execute(1);
        //create restore controller to read full backup increment
        final var restoreFullBackup = new RestoreController(
                backupDir, configuration.getFileNamePrefix(), decryptionKey, fullBackupTime);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
        final var restoreTask = RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(false)
                .deleteFilesNotInBackup(deleteLeftOver)
                .build();
        restoreFullBackup.execute(restoreTask);
        //verify, that the restore used the earlier increment
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        Assertions.assertTrue(Files.exists(realRestorePath.resolve("folder/deleted.png")));

        //recreate restore controller to read new backup increment
        final var underTest = new RestoreController(
                backupDir, configuration.getFileNamePrefix(), decryptionKey);

        //when
        underTest.execute(restoreTask);

        //then
        final var metadataParser = FileMetadataParserFactory.newInstance();
        for (final var sourceFile : originalFiles) {
            final var restoredFile = realRestorePath.resolve(sourceFile.getFileName().toString());
            assertFileIsFullyRestored(sourceFile, restoredFile, metadataParser, configuration);
        }
        final var restoredAddedFile = realRestorePath.resolve(added.getFileName().toString());
        assertFileIsFullyRestored(added, restoredAddedFile, metadataParser, configuration);
        final var restoredChangedFile = realRestorePath.resolve(changed.getFileName().toString());
        assertFileIsFullyRestored(changed, restoredChangedFile, metadataParser, configuration);
        final var restoredInternal = Files.readSymbolicLink(realRestorePath.resolve("folder/internal.png")).toAbsolutePath();
        Assertions.assertEquals(sourceDir.relativize(internalLinkTarget), realRestorePath.relativize(restoredInternal));
        final var restoredExternal = Files.readSymbolicLink(realRestorePath.resolve("external.png")).toAbsolutePath();
        Assertions.assertEquals(externalLinkTarget, restoredExternal);
        final var restoredAddedLink = Files.readSymbolicLink(realRestorePath.resolve("folder/added-external.png")).toAbsolutePath();
        Assertions.assertEquals(restoredAddedLink, restoredExternal);
        Assertions.assertEquals(deleteLeftOver, Files.notExists(realRestorePath.resolve("deleted.txt")));
        Assertions.assertEquals(deleteLeftOver, Files.notExists(realRestorePath.resolve("folder/deleted.png")));
        assertFileMetadataMatches(sourceFolder, realRestorePath.resolve("folder"), metadataParser, configuration);
    }

    @ParameterizedTest
    @MethodSource("restoreParameterProvider")
    void testExecuteShouldNotRestoreAnyFilesWhenExecutedWithValidInputUsingDryRun(
            final PublicKey encryptionKey, final PrivateKey decryptionKey,
            final int threads, final HashAlgorithm hash) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(BackupType.FULL, sourceDir, backupDir, encryptionKey, hash);

        final var sourceFiles = List.of(
                sourceDir.resolve("A.png"),
                sourceDir.resolve("B.png"),
                sourceDir.resolve("C.png"));
        for (final var sourceFile : sourceFiles) {
            FileUtils.copyFile(getExampleResource(), sourceFile.toFile());
        }
        final var sourceFolder = sourceDir.resolve("folder");
        Files.createDirectories(sourceFolder);

        final var sourceLinkInternal = sourceDir.resolve("folder/internal.png");
        final var internalLinkTarget = sourceDir.resolve("A.png");
        Files.createSymbolicLink(sourceLinkInternal, internalLinkTarget);
        final var sourceLinkExternal = sourceDir.resolve("external.png");
        final var externalLinkTarget = getExampleResource().toPath().toAbsolutePath();
        Files.createSymbolicLink(sourceLinkExternal, externalLinkTarget);

        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);

        final var underTest = new RestoreController(
                backupDir, configuration.getFileNamePrefix(), decryptionKey);
        final var restoreTargets = new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));

        //when
        underTest.execute(RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(threads)
                .dryRun(true)
                .build());

        //then
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        for (final var sourceFile : sourceFiles) {
            final var restoredFile = realRestorePath.resolve(sourceFile.getFileName().toString());
            Assertions.assertFalse(Files.exists(restoredFile), "File should not exist: " + restoredFile);
        }
        final var internal = realRestorePath.resolve("folder/internal.png");
        final var external = realRestorePath.resolve("external.png");
        final var folder = realRestorePath.resolve("folder");
        Stream.of(internal, external, folder)
                .forEach(file -> Assertions.assertFalse(Files.exists(file), "File should not exist: " + file));
    }

    private static void assertFileIsFullyRestored(
            final Path sourceFile,
            final Path restoredFile,
            final FileMetadataParser metadataParser,
            final BackupJobConfiguration configuration) throws IOException {
        final var expectedBytes = Files.readAllBytes(sourceFile);
        Assertions.assertTrue(Files.exists(restoredFile, LinkOption.NOFOLLOW_LINKS), "File should exist: " + restoredFile);
        final var actualBytes = Files.readAllBytes(restoredFile);
        Assertions.assertArrayEquals(expectedBytes, actualBytes);
        assertFileMetadataMatches(sourceFile, restoredFile, metadataParser, configuration);
    }

    private static void assertFileMetadataMatches(
            final Path sourceFile,
            final Path restoredFile,
            final FileMetadataParser metadataParser,
            final BackupJobConfiguration configuration) {
        final var actualMetadata = metadataParser.parse(restoredFile.toFile(), configuration);
        final var expectedMetadata = metadataParser.parse(sourceFile.toFile(), configuration);
        Assertions.assertEquals(expectedMetadata.getHidden(), actualMetadata.getHidden(),
                "File should be hidden: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getOwner(), actualMetadata.getOwner(),
                "File should be owned by user: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getGroup(), actualMetadata.getGroup(),
                "File should be owned by group: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getPosixPermissions(), actualMetadata.getPosixPermissions(),
                "File should have correct permissions: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getLastModifiedUtcEpochSeconds(), actualMetadata.getLastModifiedUtcEpochSeconds(),
                "File should have correct last modified time: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getCreatedUtcEpochSeconds(), actualMetadata.getCreatedUtcEpochSeconds(),
                "File should have correct creation time: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getOriginalSizeBytes(), actualMetadata.getOriginalSizeBytes(),
                "File should have correct size: " + restoredFile);
        Assertions.assertEquals(expectedMetadata.getOriginalHash(), actualMetadata.getOriginalHash(),
                "File should have correct hash: " + restoredFile);
    }

    private File getExampleResource() {
        return new File(Objects.requireNonNull(getClass().getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile());
    }

    private BackupJobConfiguration getBackupJobConfiguration(
            final BackupType type, final Path source, final Path backup, final PublicKey encryptionKey, final HashAlgorithm hashAlgorithm) {
        return BackupJobConfiguration.builder()
                .backupType(type)
                .fileNamePrefix("test")
                .compression(CompressionAlgorithm.BZIP2)
                .hashAlgorithm(hashAlgorithm)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(backup)
                .sources(Set.of(BackupSource.builder()
                        .path(source)
                        .build()))
                .chunkSizeMebibyte(1)
                .encryptionKey(encryptionKey)
                .build();
    }

}
