package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.progress.NoOpProgressTracker;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class ManifestManagerImplIntegrationTest extends TempFileAwareTest {

    @Test
    @SuppressWarnings("java:S2925")
    void testMergeForRestoreShouldKeepLatestFileSetWhenCalledWithValidIncrementalData() throws IOException, InterruptedException {
        //given
        final var dataStore = DataStore.newInMemoryInstance();
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var underTest = new ManifestManagerImpl(dataStore, NoOpProgressTracker.INSTANCE);
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var source = testDataRoot.resolve("source");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(source)).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.INCREMENTAL)
                .build();
        final var file1 = source.resolve("file1.txt");
        final var file2 = source.resolve("file2.txt");
        final var file1Copy = source.resolve("file1copy.txt");
        Files.createDirectories(source);
        Files.createFile(file1);
        Files.writeString(file1, "content1");
        Files.createFile(file2);
        Files.writeString(file2, "content2");
        Files.createFile(file1Copy);
        Files.writeString(file1Copy, "content1");
        final var parser = FileMetadataParserFactory.newInstance();
        final var original = underTest.generateManifest(config, BackupType.FULL, 0);
        final var origFile1 = parser.parse(file1.toFile(), config);
        final var origFile2 = parser.parse(file2.toFile(), config);
        final var origFile1Copy = parser.parse(file1Copy.toFile(), config);
        final var origArchiveFile1 = getArchivedFileMetadata(
                Set.of(origFile1, origFile1Copy),
                original.getVersions().last(),
                Optional.empty());
        final var origArchiveFile2 = getArchivedFileMetadata(
                Set.of(origFile2),
                original.getVersions().last(),
                Optional.empty());
        original.getFiles()
                .putAll(Stream.of(origFile1, origFile2, origFile1Copy)
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity())));
        original.getArchivedEntries()
                .putAll(Stream.of(origArchiveFile1, origArchiveFile2)
                .collect(Collectors.toMap(ArchivedFileMetadata::getId, Function.identity())));
        Thread.sleep(Duration.ofSeconds(1).toMillis());
        final var incremental = underTest.generateManifest(config, BackupType.INCREMENTAL, 1);
        FileUtils.deleteQuietly(file2.toFile());
        Files.writeString(file1, "content1-changed");
        final var incFile1 = parser.parse(file1.toFile(), config);
        final var incFile1Copy = parser.parse(file1Copy.toFile(), config);
        final var incArchiveFile1 = getArchivedFileMetadata(
                Set.of(incFile1),
                incremental.getVersions().last(),
                Optional.empty());
        final var incArchiveFile1Copy = getArchivedFileMetadata(
                Set.of(incFile1Copy),
                original.getVersions().last(),
                Optional.of(origArchiveFile1.getId()));
        incremental.getFiles()
                .putAll(Stream.of(incFile1, incFile1Copy)
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity())));
        incremental.getArchivedEntries()
                .putAll(Stream.of(incArchiveFile1, incArchiveFile1Copy)
                .collect(Collectors.toMap(ArchivedFileMetadata::getId, Function.identity())));

        //when
        final var actual = underTest.mergeForRestore(new TreeMap<>(Map.of(0, original, 1, incremental)));

        //then
        Assertions.assertNotNull(actual);
        //The deleted file will be filtered out
        final var actualMap = fileMetadataSetRepository.findAll(actual.getFiles(), 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity()));
        Assertions.assertEquals(incremental.getFiles(), actualMap);
    }

    private static ArchivedFileMetadata getArchivedFileMetadata(
            final Set<FileMetadata> files,
            final int increment,
            final Optional<UUID> entryNameOptional) {
        final var archiveId = UUID.randomUUID();
        files.forEach(f -> f.setArchiveMetadataId(archiveId));
        return ArchivedFileMetadata.builder()
                .id(archiveId)
                .files(files.stream().map(FileMetadata::getId).collect(Collectors.toSet()))
                .archivedHash("hash")
                .archiveLocation(ArchiveEntryLocator.builder()
                        .backupIncrement(increment)
                        .entryName(entryNameOptional.orElse(archiveId))
                        .build())
                .originalHash(files.stream().findFirst().map(FileMetadata::getOriginalHash).orElse(null))
                .build();
    }
}
