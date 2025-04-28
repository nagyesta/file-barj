package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;

class FileMetadataChangeDetectorFactoryTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateShouldThrowExceptionWhenCalledWithNullConfiguration() {
        //given
        final Map<String, Map<UUID, FileMetadata>> map = Map.of("key", Map.of());

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory
                        .create(null, map, PermissionComparisonStrategy.STRICT));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateShouldThrowExceptionWhenCalledWithNullFiles() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory
                        .create(mock(BackupJobConfiguration.class), null, PermissionComparisonStrategy.STRICT));

        //then + exception
    }

    @Test
    void testCreateShouldReturnSimpleFileMetadataChangeDetectorWhenCalledWithNoneHashAlgorithm() {
        //given
        final var config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .chunkSizeMebibyte(1)
                .compression(CompressionAlgorithm.BZIP2)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .sources(Set.of())
                .fileNamePrefix("prefix")
                .destinationDirectory(testDataRoot)
                .build();

        //when
        final var actual = FileMetadataChangeDetectorFactory
                .create(config, Map.of("key", Map.of()), PermissionComparisonStrategy.STRICT);

        //then
        Assertions.assertInstanceOf(SimpleFileMetadataChangeDetector.class, actual);
    }

    @Test
    void testCreateShouldReturnHashingFileMetadataChangeDetectorWhenCalledWithSha256HashAlgorithm() {
        //given
        final var config = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .chunkSizeMebibyte(1)
                .compression(CompressionAlgorithm.BZIP2)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .sources(Set.of())
                .fileNamePrefix("prefix")
                .destinationDirectory(testDataRoot)
                .build();

        //when
        final var actual = FileMetadataChangeDetectorFactory
                .create(config, Map.of("key", Map.of()), PermissionComparisonStrategy.STRICT);

        //then
        Assertions.assertInstanceOf(HashingFileMetadataChangeDetector.class, actual);
    }
}
