package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.AppVersion;
import com.github.nagyesta.filebarj.core.model.RestoreManifest;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class RestorePipelineIntegrationTest extends TempFileAwareTest {

    private static final int FUTURE = 99;

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithAManifestCreatedByAFutureVersion() {
        //given
        final var manifest = mock(RestoreManifest.class);
        doReturn(new AppVersion(FUTURE, FUTURE, FUTURE)).when(manifest).getMaximumAppVersion();
        doReturn(mock(BackupJobConfiguration.class)).when(manifest).getConfiguration();
        doReturn(Map.of()).when(manifest).getFiles();
        final var sourceDirectory = testDataRoot.resolve("source-dir");
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestorePipeline(manifest, backupDirectory, restoreTargets, null));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithAManifestWhichHasNoConfiguration() {
        //given
        final var manifest = mock(RestoreManifest.class);
        doReturn(new AppVersion()).when(manifest).getMaximumAppVersion();
        doReturn(null).when(manifest).getConfiguration();
        doReturn(Map.of()).when(manifest).getFiles();
        final var sourceDirectory = testDataRoot.resolve("source-dir");
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestorePipeline(manifest, backupDirectory, restoreTargets, null));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithAManifestWhichHasNoFiles() {
        //given
        final var manifest = mock(RestoreManifest.class);
        doReturn(new AppVersion()).when(manifest).getMaximumAppVersion();
        doReturn(mock(BackupJobConfiguration.class)).when(manifest).getConfiguration();
        doReturn(null).when(manifest).getFiles();
        final var sourceDirectory = testDataRoot.resolve("source-dir");
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestorePipeline(manifest, backupDirectory, restoreTargets, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        final var sourceDirectory = testDataRoot.resolve("source-dir");
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestorePipeline(null, backupDirectory, restoreTargets, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullSourcePath() throws IOException {
        //given
        final var backupController = executeABackup();
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var sourceDirectory = getSourceDirectory(backupController);
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestorePipeline(restoreManifest, null, restoreTargets, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullTargetPath() throws IOException {
        //given
        final var backupController = executeABackup();
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var backupDirectory = testDataRoot.resolve("backup-dir");

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RestorePipeline(restoreManifest, backupDirectory, null, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFinalizePermissionsShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var backupController = executeABackup();
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var sourceDirectory = getSourceDirectory(backupController);
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        final var underTest = new RestorePipeline(restoreManifest, backupDirectory, restoreTargets, null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.finalizePermissions(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testRestoreFilesShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var backupController = executeABackup();
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var sourceDirectory = getSourceDirectory(backupController);
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        final var underTest = new RestorePipeline(restoreManifest, backupDirectory, restoreTargets, null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.restoreFiles(null, 1));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testEvaluateRestoreSuccessShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var backupController = executeABackup();
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var sourceDirectory = getSourceDirectory(backupController);
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        final var underTest = new RestorePipeline(restoreManifest, backupDirectory, restoreTargets, null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.evaluateRestoreSuccess(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testRestoreDirectoriesShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var backupController = executeABackup();
        final var backupDirectory = testDataRoot.resolve("backup-dir");
        final var restoreDirectory = testDataRoot.resolve("restore-dir");
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var sourceDirectory = getSourceDirectory(backupController);
        final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

        final var underTest = new RestorePipeline(restoreManifest, backupDirectory, restoreTargets, null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.restoreDirectories(null));

        //then + exception
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void testPartialRestoreShouldRestoreFilesToDestinationWhenExecutedWithValidInput(final int threads) throws IOException {
        //given
        final var sourceDir = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backupDir = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var restoreDir = testDataRoot.resolve("restore-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(sourceDir, backupDir);

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
        final var manifest = backupController.getManifest();
        final var restoreManifest = new ManifestManagerImpl().mergeForRestore(new TreeMap<>(Map.of(0, manifest)));
        final var restoreTargets = getRestoreTargets(sourceDir, restoreDir);

        final var underTest = new RestorePipeline(restoreManifest, backupDir, restoreTargets, null);
        final var scope = manifest.getFiles().values().stream()
                .filter(f -> f.getAbsolutePath().equals(sourceLinkExternal)
                        || f.getAbsolutePath().endsWith("A.png")
                        || f.getAbsolutePath().equals(sourceDir))
                .toList();

        final var scopeMap = scope.stream()
                .filter(f -> f.getArchiveMetadataId() != null)
                .toList();

        //when
        underTest.restoreDirectories(scope);
        underTest.restoreFiles(scopeMap, threads);
        underTest.finalizePermissions(scope);

        //then
        final var realRestorePath = restoreTargets.mapToRestorePath(sourceDir);
        final var restoredExternal = Files.readSymbolicLink(realRestorePath.resolve("external.png")).toAbsolutePath();
        Assertions.assertEquals(externalLinkTarget, restoredExternal);
    }

    private Path getSourceDirectory(final BackupController backupController) {
        return backupController.getManifest().getConfiguration().getSources().stream()
                .findAny()
                .map(BackupSource::getPath)
                .orElse(testDataRoot);
    }

    private RestoreTargets getRestoreTargets(final Path sourceDir, final Path restoreDir) {
        return new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
    }

    private BackupController executeABackup() throws IOException {
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backup = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(source, backup);
        FileUtils.copyFile(getExampleResource(), source.resolve("A.png").toFile());
        final var backupController = new BackupController(configuration, true);
        backupController.execute(1);
        return backupController;
    }

    private File getExampleResource() {
        return new File(Objects.requireNonNull(getClass().getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile());
    }

    private BackupJobConfiguration getBackupJobConfiguration(
            final Path source, final Path backup) {
        return BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .fileNamePrefix("test")
                .compression(CompressionAlgorithm.BZIP2)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(backup)
                .sources(Set.of(BackupSource.builder()
                        .path(source)
                        .build()))
                .chunkSizeMebibyte(1)
                .encryptionKey(null)
                .build();
    }

}
