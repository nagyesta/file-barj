package com.github.nagyesta.filebarj.core.restore.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupParameters;
import com.github.nagyesta.filebarj.core.common.ManifestManagerImpl;
import com.github.nagyesta.filebarj.core.common.PermissionComparisonStrategy;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.AppVersion;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.RestoreManifest;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.progress.NoOpProgressTracker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.function.Consumers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class RestorePipelineIntegrationTest extends TempFileAwareTest {

    private static final int FUTURE = 99;
    private static final TreeMap<String, TreeSet<Integer>> PREFIX_MAP = new TreeMap<>(Map.of("prefix", new TreeSet<>(Set.of(0))));

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithAManifestCreatedByAFutureVersion(final DataStore dataStore) {
        //given
        try (dataStore) {
            final var manifest = mock(RestoreManifest.class);
            doReturn(new AppVersion(FUTURE, FUTURE, FUTURE)).when(manifest).getMaximumAppVersion();
            doReturn(mock(BackupJobConfiguration.class)).when(manifest).getConfiguration();
            doReturn(new FileMetadataSetId(Consumers.nop())).when(manifest).getFiles();
            final var sourceDirectory = testDataRoot.resolve("source-dir");
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var restoreTargets = getRestoreTargets(BackupPath.of(sourceDirectory), restoreDirectory);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new RestorePipeline(dataStore, manifest, backupDirectory, restoreTargets, null, null));

            //then + exception
        }
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithAManifestWhichHasNoConfiguration(final DataStore dataStore) {
        //given
        try (dataStore) {
            final var fileSet = dataStore.fileMetadataSetRepository().createFileSet();
            final var manifest = mock(RestoreManifest.class);
            doReturn(new AppVersion()).when(manifest).getMaximumAppVersion();
            doReturn(null).when(manifest).getConfiguration();
            doReturn(PREFIX_MAP).when(manifest).getFileNamePrefixes();
            doReturn(fileSet).when(manifest).getFiles();
            final var sourceDirectory = testDataRoot.resolve("source-dir");
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var restoreTargets = getRestoreTargets(BackupPath.of(sourceDirectory), restoreDirectory);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new RestorePipeline(dataStore, manifest, backupDirectory, restoreTargets, null, null));

            //then + exception
        }
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithAManifestWhichHasNoFiles(final DataStore dataStore) {
        //given
        try (dataStore) {
            final var manifest = mock(RestoreManifest.class);
            doReturn(new AppVersion()).when(manifest).getMaximumAppVersion();
            doReturn(PREFIX_MAP).when(manifest).getFileNamePrefixes();
            doReturn(mock(BackupJobConfiguration.class)).when(manifest).getConfiguration();
            doReturn(new FileMetadataSetId(UUID.randomUUID(), a -> {
            })).when(manifest).getFiles();
            final var sourceDirectory = testDataRoot.resolve("source-dir");
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var restoreTargets = getRestoreTargets(BackupPath.of(sourceDirectory), restoreDirectory);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new RestorePipeline(dataStore, manifest, backupDirectory, restoreTargets, null, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithNullManifest(final DataStore dataStore) {
        //given
        try (dataStore) {
            final var sourceDirectory = testDataRoot.resolve("source-dir");
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var restoreTargets = getRestoreTargets(BackupPath.of(sourceDirectory), restoreDirectory);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new RestorePipeline(dataStore, null, backupDirectory, restoreTargets, null, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testConstructorShouldThrowExceptionWhenCalledWithNullSourcePath(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            try (var restoreManifest = manifestManager.mergeForRestore(loaded)) {
                final var restoreDirectory = testDataRoot.resolve("restore-dir");
                final var sourceDirectory = getSourceDirectory(backupController);
                final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

                //when
                Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new RestorePipeline(dataStore, restoreManifest, null, restoreTargets, null, null));

                //then + exception
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testConstructorShouldThrowExceptionWhenCalledWithNullTargetPath(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            try (var backupController = executeABackup()) {
                final var manifest = backupController.getManifest();

                final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
                final var loaded = reloadManifest(manifestManager, manifest);

                try (var restoreManifest = manifestManager.mergeForRestore(loaded)) {
                    final var backupDirectory = testDataRoot.resolve("backup-dir");

                    //when
                    Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new RestorePipeline(dataStore, restoreManifest, backupDirectory, null, null, null));

                    //then + exception
                }
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testFinalizePermissionsShouldThrowExceptionWhenCalledWithNullFiles(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.finalizePermissions(null, mock(ForkJoinPool.class)));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testFinalizePermissionsShouldThrowExceptionWhenCalledWithNullMap(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);
            final var fileSet = loaded.get(0).getFiles();

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.finalizePermissions(fileSet, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testRestoreFilesShouldThrowExceptionWhenCalledWithNullContentSources(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.restoreFiles(null, mock(ForkJoinPool.class)));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testRestoreFilesShouldThrowExceptionWhenCalledWithNullThreadPool(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);
            final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
            final var fileSet = fileMetadataSetRepository.createFileSet();
            final var contentSources = fileMetadataSetRepository.findAll(loaded.get(0).getFiles(), 0, Integer.MAX_VALUE)
                    .stream()
                    .filter(fileMetadata -> fileMetadata.getFileType().isContentSource())
                    .toList();
            fileMetadataSetRepository.appendTo(fileSet, contentSources);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.restoreFiles(fileSet, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testEvaluateRestoreSuccessShouldThrowExceptionWhenCalledWithNullFiles(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.evaluateRestoreSuccess(null, mock(ForkJoinPool.class)));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testEvaluateRestoreSuccessShouldThrowExceptionWhenCalledWithNullThreadPool(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            try (var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null)) {
                final var fileSet = loaded.get(0).getFiles();

                //when
                Assertions.assertThrows(IllegalArgumentException.class,
                        () -> underTest.evaluateRestoreSuccess(fileSet, null));

                //then + exception
            }
        }
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testEvaluateRestoreSuccessShouldNotThrowExceptionWhenCalledWithoutRestoringBackup(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            try (var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null)) {
                final var fileSet = loaded.get(0).getFiles();

                final var threadPool = new ForkJoinPool(1);
                try {
                    //when
                    underTest.evaluateRestoreSuccess(fileSet, threadPool);

                    //then no exception
                } catch (final Exception e) {
                    Assertions.fail(e.getMessage());
                } finally {
                    threadPool.shutdownNow();
                }
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testRestoreDirectoriesShouldThrowExceptionWhenCalledWithNull(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.restoreDirectories(null));

            //then + exception
        }
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProviderWithMultiThreads")
    @DisabledOnOs(WINDOWS)
    void testPartialRestoreShouldRestoreFilesToDestinationWhenExecutedWithValidInput(
            final int threads,
            final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
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

            final var parameters = BackupParameters.builder()
                    .job(configuration)
                    .forceFull(true)
                    .build();
            try (var backupController = new BackupController(parameters)) {
                backupController.execute(1);
                final var manifest = backupController.getManifest();

                final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
                final var loaded = reloadManifest(manifestManager, manifest);

                final var restoreManifest = manifestManager.mergeForRestore(loaded);
                final var restoreTargets = getRestoreTargets(BackupPath.of(sourceDir), restoreDir);

                final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
                final var underTest = new RestorePipeline(
                        dataStore, restoreManifest, backupDir, restoreTargets, null, PermissionComparisonStrategy.STRICT);
                final var scope = fileMetadataSetRepository.createFileSet();
                final var scopeList = fileMetadataSetRepository.findAll(loaded.get(0).getFiles(), 0, Integer.MAX_VALUE)
                        .stream()
                        .filter(f -> f.getAbsolutePath().toOsPath().equals(sourceLinkExternal)
                                || f.getAbsolutePath().toOsPath().endsWith("A.png")
                                || f.getAbsolutePath().toOsPath().equals(sourceDir))
                        .toList();
                fileMetadataSetRepository.appendTo(scope, scopeList);
                final var scopeMap = fileMetadataSetRepository.createFileSet();
                fileMetadataSetRepository.appendTo(scopeMap, scopeList.stream()
                        .filter(f -> f.getArchiveMetadataId() != null)
                        .toList());

                final var threadPool = new ForkJoinPool(threads);
                try {
                    //when
                    underTest.restoreDirectories(scope);
                    underTest.restoreFiles(scopeMap, threadPool);
                    underTest.finalizePermissions(scope, threadPool);
                } finally {
                    threadPool.shutdownNow();
                }

                //then
                final var realRestorePath = restoreTargets.mapToRestorePath(BackupPath.of(sourceDir));
                final var restoredExternal = Files.readSymbolicLink(realRestorePath.resolve("external.png")).toAbsolutePath();
                Assertions.assertEquals(externalLinkTarget, restoredExternal);
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    @DisabledOnOs(WINDOWS)
    void testDeleteLeftOverFilesShouldThrowExceptionWhenCalledWithNullThreadPool(final DataStore dataStore) throws IOException {
        //given
        try (dataStore) {
            final var backupController = executeABackup();
            final var backupDirectory = testDataRoot.resolve("backup-dir");
            final var restoreDirectory = testDataRoot.resolve("restore-dir");
            final var manifest = backupController.getManifest();

            final var manifestManager = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
            final var loaded = reloadManifest(manifestManager, manifest);

            final var restoreManifest = manifestManager.mergeForRestore(loaded);
            final var sourceDirectory = getSourceDirectory(backupController);
            final var restoreTargets = getRestoreTargets(sourceDirectory, restoreDirectory);

            final var underTest = new RestorePipeline(dataStore, restoreManifest, backupDirectory, restoreTargets, null, null);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.deleteLeftOverFiles(null, true, null));

            //then + exception
        }
    }

    private BackupPath getSourceDirectory(final BackupController backupController) {
        return backupController.getManifest().getConfiguration().getSources().stream()
                .findAny()
                .map(BackupSource::getPath)
                .orElse(BackupPath.of(testDataRoot));
    }

    private RestoreTargets getRestoreTargets(
            final BackupPath sourceDir,
            final Path restoreDir) {
        return new RestoreTargets(Set.of(new RestoreTarget(sourceDir, restoreDir)));
    }

    private BackupController executeABackup() throws IOException {
        final var source = testDataRoot.resolve("source-dir" + UUID.randomUUID());
        final var backup = testDataRoot.resolve("backup-dir" + UUID.randomUUID());
        final var configuration = getBackupJobConfiguration(source, backup);
        FileUtils.copyFile(getExampleResource(), source.resolve("A.png").toFile());
        final var parameters = BackupParameters.builder()
                .job(configuration)
                .forceFull(true)
                .build();
        final var backupController = new BackupController(parameters);
        backupController.execute(1);
        return backupController;
    }

    private SortedMap<Integer, BackupIncrementManifest> reloadManifest(
            final ManifestManagerImpl manifestManager,
            final BackupIncrementManifest manifest) {
        final var destinationDirectory = manifest.getConfiguration().getDestinationDirectory();
        final var fileNamePrefix = manifest.getFileNamePrefix();
        return manifestManager.load(destinationDirectory, fileNamePrefix, null, Long.MAX_VALUE);
    }

    private File getExampleResource() {
        return new File(Objects.requireNonNull(getClass().getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile());
    }

    private BackupJobConfiguration getBackupJobConfiguration(
            final Path source,
            final Path backup) {
        return BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .fileNamePrefix("test")
                .compression(CompressionAlgorithm.BZIP2)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(backup)
                .sources(Set.of(BackupSource.builder()
                        .path(BackupPath.of(source))
                        .build()))
                .chunkSizeMebibyte(1)
                .encryptionKey(null)
                .build();
    }
}
