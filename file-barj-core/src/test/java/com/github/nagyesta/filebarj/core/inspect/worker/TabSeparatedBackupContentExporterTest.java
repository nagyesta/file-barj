package com.github.nagyesta.filebarj.core.inspect.worker;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.AppVersion;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TabSeparatedBackupContentExporterTest extends TempFileAwareTest {

    private static final String OWNER = "owner";
    private static final String GROUP = "group";
    private static final String PERMISSIONS = "rwxr-x---";
    private static final long LAST_MODIFIED_UTC_EPOCH_SECONDS = 1L;
    private static final long ORIGINAL_SIZE_BYTES = 1024L;
    private static final String ORIGINAL_HASH = "hash";

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testWriteManifestContentShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        final var underTest = new TabSeparatedBackupContentExporter();
        final var path = testDataRoot.resolve(UUID.randomUUID() + ".tsv");

        //when
        assertThrows(IllegalArgumentException.class, () -> underTest.writeManifestContent(null, path));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testWriteManifestContentShouldThrowExceptionWhenCalledWithNullOutputPath() {
        //given
        final var underTest = new TabSeparatedBackupContentExporter();
        final var manifest = mock(BackupIncrementManifest.class);

        //when
        assertThrows(IllegalArgumentException.class, () -> underTest.writeManifestContent(manifest, null));

        //then + exception
    }

    @Test
    void testWriteManifestContentShouldExportContentWhenCalledWithValidInput() throws IOException {
        //given
        final var underTest = new TabSeparatedBackupContentExporter();
        final var fileId = UUID.randomUUID();
        final var absolutePath = FilenameUtils.separatorsToUnix(testDataRoot.resolve("path.txt").toAbsolutePath().toString());
        final var manifest = BackupIncrementManifest.builder()
                .files(Map.of(fileId, FileMetadata.builder()
                        .fileSystemKey("key")
                        .owner(OWNER)
                        .group(GROUP)
                        .posixPermissions(PERMISSIONS)
                        .id(fileId)
                        .lastAccessedUtcEpochSeconds(0L)
                        .createdUtcEpochSeconds(0L)
                        .lastModifiedUtcEpochSeconds(LAST_MODIFIED_UTC_EPOCH_SECONDS)
                        .originalSizeBytes(ORIGINAL_SIZE_BYTES)
                        .originalHash(ORIGINAL_HASH)
                        .absolutePath(BackupPath.ofPathAsIs(absolutePath))
                        .fileType(FileType.REGULAR_FILE)
                        .status(Change.NEW)
                        .build()))
                .appVersion(new AppVersion("1.2.3"))
                .configuration(BackupJobConfiguration.builder()
                        .encryptionKey(EncryptionUtil.generateRsaKeyPair().getPublic())
                        .compression(CompressionAlgorithm.GZIP)
                        .chunkSizeMebibyte(1)
                        .fileNamePrefix("prefix")
                        .hashAlgorithm(HashAlgorithm.SHA256)
                        .destinationDirectory(testDataRoot)
                        .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                        .sources(Set.of())
                        .backupType(BackupType.FULL)
                        .build())
                .backupType(BackupType.FULL)
                .versions(new TreeSet<>(Set.of(0)))
                .fileNamePrefix("prefix")
                .build();
        final var path = testDataRoot.resolve(UUID.randomUUID() + ".tsv");

        //when
        underTest.writeManifestContent(manifest, path);

        //then
        final var actual = Files.readAllLines(path);
        Assertions.assertEquals(2, actual.size());
        Assertions.assertEquals("permissions\towner\tgroup\tsize\tlast_modified\thash_sha256\tpath", actual.get(0));
        final var expected = PERMISSIONS + "\t" + OWNER + "\t" + GROUP + "\t" + ORIGINAL_SIZE_BYTES + "\t"
                + Instant.ofEpochSecond(LAST_MODIFIED_UTC_EPOCH_SECONDS) + "\t" + ORIGINAL_HASH + "\t" + absolutePath;
        Assertions.assertEquals(expected, actual.get(1));
    }
}
