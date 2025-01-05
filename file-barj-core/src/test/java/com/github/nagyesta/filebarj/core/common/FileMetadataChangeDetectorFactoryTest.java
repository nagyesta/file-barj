package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.*;

class FileMetadataChangeDetectorFactoryTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateShouldThrowExceptionWhenCalledWithNullDatabase() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory
                        .create(null, PermissionComparisonStrategy.STRICT));

        //then + exception
    }

    @Test
    void testCreateShouldThrowExceptionWhenCalledWithEmptyDatabase() {
        //given
        final var manifestDatabase = mock(InMemoryManifestDatabase.class);
        when(manifestDatabase.isEmpty()).thenReturn(true);
        when(manifestDatabase.getLatestConfiguration()).thenReturn(mock(BackupJobConfiguration.class));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> FileMetadataChangeDetectorFactory
                        .create(manifestDatabase, PermissionComparisonStrategy.STRICT));

        //then + exception
        verify(manifestDatabase, atLeastOnce()).isEmpty();
        verify(manifestDatabase, never()).getLatestConfiguration();
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
        final var manifestDatabase = mock(InMemoryManifestDatabase.class);
        when(manifestDatabase.isEmpty()).thenReturn(false);
        when(manifestDatabase.getLatestConfiguration()).thenReturn(config);

        //when
        final var actual = FileMetadataChangeDetectorFactory
                .create(manifestDatabase, PermissionComparisonStrategy.STRICT);

        //then
        Assertions.assertInstanceOf(SimpleFileMetadataChangeDetector.class, actual);
        verify(manifestDatabase, atLeastOnce()).isEmpty();
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
        final var manifestDatabase = mock(InMemoryManifestDatabase.class);
        when(manifestDatabase.isEmpty()).thenReturn(false);
        when(manifestDatabase.getLatestConfiguration()).thenReturn(config);

        //when
        final var actual = FileMetadataChangeDetectorFactory
                .create(manifestDatabase, PermissionComparisonStrategy.STRICT);

        //then
        Assertions.assertInstanceOf(HashingFileMetadataChangeDetector.class, actual);
        verify(manifestDatabase, atLeastOnce()).isEmpty();
    }
}
