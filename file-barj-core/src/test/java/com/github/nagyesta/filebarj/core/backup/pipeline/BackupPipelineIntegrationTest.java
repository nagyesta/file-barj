package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserLocal;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

class BackupPipelineIntegrationTest extends TempFileAwareTest {

    private static final AppVersion APP_VERSION = new AppVersion(1, 2, 3);
    private static final int START_TIME_UTC_EPOCH_SECONDS = 12345;

    @Test
    void testStoreEntryShouldAddFileEntryToArchiveWhenNoEncryptionIsUsed() {
        //given
        final var config = getConfiguration(DuplicateHandlingStrategy.KEEP_EACH);
        final var file = getRegularFileMetadata(config);
        final var manifest = getManifest(config);
        try (var underTest = new BackupPipeline(manifest)) {
            //when
            final var actual = underTest.storeEntries(List.of(List.of(file)));
            underTest.close();
            final var files = underTest.getDataFilesWritten();
            final var indexFile = underTest.getIndexFileWritten();

            //then
            final var expected = getExpected(indexFile, file);
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertEquals(1, files.size());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testStoreEntryShouldAddFileEntryOnlyOnceToArchiveWhenCalledWithDuplicatesAndKeepOnePerBackupStrategyIsUsed() {
        //given
        final var config = getConfiguration(DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP);
        final var file1 = getRegularFileMetadata(config);
        final var file2 = getRegularFileMetadata(config);
        final var file3 = getRegularFileMetadata(config);
        final var manifest = getManifest(config);
        try (var underTest = new BackupPipeline(manifest)) {
            //when
            final var actual = underTest.storeEntries(List.of(List.of(file1, file2, file3)));
            underTest.close();
            final var files = underTest.getDataFilesWritten();
            final var indexFile = underTest.getIndexFileWritten();

            //then
            final var expected = getExpected(indexFile, file1, file2, file3);
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertEquals(1, files.size());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testStoreEntryShouldAddSymbolicLinkEntryToArchiveWhenNoEncryptionIsUsed() throws IOException {
        //given
        final var config = getConfiguration(DuplicateHandlingStrategy.KEEP_EACH);
        final var file = getSymbolicLinkMetadata(config);
        final var manifest = getManifest(config);
        try (var underTest = new BackupPipeline(manifest)) {
            //when
            final var actual = underTest.storeEntries(List.of(List.of(file)));
            underTest.close();
            final var files = underTest.getDataFilesWritten();
            final var indexFile = underTest.getIndexFileWritten();

            //then
            final var expected = getExpected(indexFile, file);
            Assertions.assertEquals(expected, actual.get(0));
            Assertions.assertEquals(1, files.size());
        } catch (final Exception e) {
            Assertions.fail(e);
        }
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given
        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new BackupPipeline(null));

        //then + exception
    }

    @SuppressWarnings({"DataFlowIssue"})
    @Test
    void testStoreEntityShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var config = getConfiguration(DuplicateHandlingStrategy.KEEP_EACH);
        final var manifest = getManifest(config);
        try (var underTest = new BackupPipeline(manifest)) {
            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.storeEntries(null));

            //then + exception
        } catch (final Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    void testStoreEntityShouldThrowExceptionWhenCalledWithNullInList() {
        //given
        final var config = getConfiguration(DuplicateHandlingStrategy.KEEP_EACH);
        final var manifest = getManifest(config);
        try (var underTest = new BackupPipeline(manifest)) {
            final List<List<FileMetadata>> list = new ArrayList<>();
            list.add(null);

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.storeEntries(list));

            //then + exception
        } catch (final Exception e) {
            Assertions.fail(e);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private static ArchivedFileMetadata getExpected(
            final Path indexFile, final FileMetadata... files) throws IOException {
        final var properties = new Properties();
        final var zipContentStream = Files.newInputStream(indexFile);
        properties.load(new GzipCompressorInputStream(zipContentStream));
        final var locator = ArchiveEntryLocator.fromEntryPath(properties.getProperty("00000002.path"));
        return ArchivedFileMetadata.builder()
                .id(locator.getEntryName())
                .archiveLocation(locator)
                .originalHash(files[0].getOriginalHash())
                .archivedHash(properties.getProperty("00000002.content.arch.hash"))
                .files(Arrays.stream(files).map(FileMetadata::getId).collect(Collectors.toSet()))
                .build();
    }

    private static BackupIncrementManifest getManifest(final BackupJobConfiguration config) {
        return BackupIncrementManifest.builder()
                .appVersion(APP_VERSION)
                .versions(new TreeSet<>(Set.of(0)))
                .startTimeUtcEpochSeconds(START_TIME_UTC_EPOCH_SECONDS)
                .backupType(BackupType.FULL)
                .configuration(config)
                .fileNamePrefix("pipeline-prefix-12345")
                .build();
    }

    private FileMetadata getSymbolicLinkMetadata(final BackupJobConfiguration config) throws IOException {
        final var testFile = getExampleFile();
        final var link = Path.of(testDataRoot.toAbsolutePath().toString(), UUID.randomUUID() + "-link.png");
        Files.createSymbolicLink(link, testFile.toPath());
        final var parser = new FileMetadataParserLocal();
        return parser.parse(link.toFile(), config);
    }

    private File getExampleFile() {
        return new File(Objects.requireNonNull(getClass()
                .getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile());
    }

    private FileMetadata getRegularFileMetadata(final BackupJobConfiguration config) {
        final var testFile = getExampleFile();
        final var parser = new FileMetadataParserLocal();
        return parser.parse(testFile, config);
    }

    private BackupJobConfiguration getConfiguration(final DuplicateHandlingStrategy duplicateStrategy) {
        return BackupJobConfiguration.builder()
                .compression(CompressionAlgorithm.GZIP)
                .sources(Set.of())
                .backupType(BackupType.FULL)
                .fileNamePrefix("pipeline-prefix-")
                .duplicateStrategy(duplicateStrategy)
                .destinationDirectory(testDataRoot)
                .hashAlgorithm(HashAlgorithm.SHA512)
                .build();
    }
}
