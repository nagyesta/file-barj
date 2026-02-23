package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.persistence.FileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import org.apache.commons.lang3.function.Consumers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

class FileMetadataChangeDetectorFactoryTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateShouldThrowExceptionWhenCalledWithNullConfiguration() {
        //given
        final var map = Map.of("key", new FileMetadataSetId(Consumers.nop()));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory.create(
                        null, mock(FileMetadataSetRepository.class), map, PermissionComparisonStrategy.STRICT));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateShouldThrowExceptionWhenCalledWithNullRepository() {
        //given
        final var map = Map.of("key", new FileMetadataSetId(Consumers.nop()));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory.create(
                        mock(BackupJobConfiguration.class), null, map, PermissionComparisonStrategy.STRICT));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateShouldThrowExceptionWhenCalledWithNullFiles() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory.create(
                        mock(BackupJobConfiguration.class), mock(FileMetadataSetRepository.class),
                        null, PermissionComparisonStrategy.STRICT));

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
        final var actual = FileMetadataChangeDetectorFactory.create(
                config, mock(FileMetadataSetRepository.class),
                Map.of("key", new FileMetadataSetId(Consumers.nop())), PermissionComparisonStrategy.STRICT);

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
        final var actual = FileMetadataChangeDetectorFactory.create(
                config, mock(FileMetadataSetRepository.class),
                Map.of("key", new FileMetadataSetId(Consumers.nop())), PermissionComparisonStrategy.STRICT);

        //then
        Assertions.assertInstanceOf(HashingFileMetadataChangeDetector.class, actual);
    }
}
