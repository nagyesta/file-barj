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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestToSummaryConverterTest extends TempFileAwareTest {

    private static final String OWNER = "owner";
    private static final String GROUP = "group";
    private static final String PERMISSIONS = "rwxr-x---";
    private static final long LAST_MODIFIED_UTC_EPOCH_SECONDS = 1L;
    private static final long ORIGINAL_SIZE_BYTES = 2097152L;
    private static final String ORIGINAL_HASH = "hash";

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConvertToSummaryStringShouldThrowExceptionWhenCalledWithNullManifest() {
        //given
        final var underTest = new ManifestToSummaryConverter();

        //when
        assertThrows(IllegalArgumentException.class, () -> underTest.convertToSummaryString(null));

        //then + exception
    }

    @Test
    void testConvertToSummaryStringShouldReturnExpectedValueWhenCalledWithValidManifest() {
        //given
        final var underTest = new ManifestToSummaryConverter();
        final var fileId = UUID.randomUUID();
        final var absolutePath = testDataRoot.resolve("path.txt");
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
                        .absolutePath(BackupPath.of(absolutePath))
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
                .startTimeUtcEpochSeconds(1L)
                .build();
        //when
        final var actual = underTest.convertToSummaryString(manifest);

        //then
        Assertions.assertEquals("""
                FULL backup: prefix
                \tStarted at : 1970-01-01T00:00:01Z (Epoch seconds: 1)
                \tContains 1 files (2 MiB)
                \tVersions   : [0]
                \tEncrypted  : true
                \tHash alg.  : SHA256
                \tCompression: GZIP""", actual);
    }
}
