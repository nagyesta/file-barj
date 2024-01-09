package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserLocal;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static com.github.nagyesta.filebarj.core.common.ManifestManagerImplTest.A_SECOND;

public class ManifestManagerImplIntegrationTest extends TempFileAwareTest {

    @Test
    void testMergeForRestoreShouldKeepLatestFileSetWhenCalledWithValidIncrementalData() throws IOException, InterruptedException {
        //given
        final var underTest = new ManifestManagerImpl();
        final var destinationDirectory = testDataRoot.resolve("destination");
        final var source = testDataRoot.resolve("source");
        final var config = BackupJobConfiguration.builder()
                .fileNamePrefix("prefix")
                .sources(Set.of(BackupSource.builder().path(source).build()))
                .compression(CompressionAlgorithm.GZIP)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP)
                .destinationDirectory(destinationDirectory)
                .backupType(BackupType.INCREMENTAL)
                .build();
        final var file1 = source.resolve("file1.txt");
        final var file2 = source.resolve("file2.txt");
        Files.createDirectories(source);
        Files.createFile(file1);
        Files.writeString(file1, "content1");
        Files.createFile(file2);
        Files.writeString(file2, "content2");
        final FileMetadataParser parser = new FileMetadataParserLocal();
        final var original = underTest.generateManifest(config, BackupType.FULL, 0);
        final var origFile1 = parser.parse(file1.toFile(), config);
        final var origFile2 = parser.parse(file2.toFile(), config);
        final var origArchiveId = UUID.randomUUID();
        origFile1.setArchiveMetadataId(origArchiveId);
        origFile2.setArchiveMetadataId(origArchiveId);
        original.getFiles().put(origFile1.getId(), origFile1);
        original.getFiles().put(origFile2.getId(), origFile2);
        original.getArchivedEntries().put(origArchiveId, ArchivedFileMetadata.builder()
                .id(origArchiveId)
                .files(Set.of(origFile1.getId(), origFile2.getId()))
                .archivedHash("hash")
                .archiveLocation(ArchiveEntryLocator.builder().backupIncrement(0).entryName(origArchiveId).build())
                .originalHash(origFile1.getOriginalHash())
                .build());
        Thread.sleep(A_SECOND);
        final var incremental = underTest.generateManifest(config, BackupType.INCREMENTAL, 1);
        FileUtils.deleteQuietly(file2.toFile());
        final var incFile1 = parser.parse(file1.toFile(), config);
        final var incArchiveId = UUID.randomUUID();
        incFile1.setArchiveMetadataId(incArchiveId);
        incremental.getFiles().put(incFile1.getId(), incFile1);
        incremental.getArchivedEntries().put(incArchiveId, ArchivedFileMetadata.builder()
                .id(incArchiveId)
                .files(Set.of(incFile1.getId()))
                .archivedHash("hash")
                .archiveLocation(ArchiveEntryLocator.builder().backupIncrement(0).entryName(origArchiveId).build())
                .originalHash(incFile1.getOriginalHash())
                .build());

        //when
        final var actual = underTest.mergeForRestore(new TreeMap<>(Map.of(0, original, 1, incremental)));

        //then
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(2, actual.getFiles().size());
        //The deleted file can be filtered out
        Assertions.assertEquals(Map.of(origFile1.getId(), origFile1), actual.getFiles().get(original.getFileNamePrefix()));
        Assertions.assertEquals(incremental.getFiles(), actual.getFiles().get(incremental.getFileNamePrefix()));
    }
}
