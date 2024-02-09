package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

class DefaultBackupScopePartitionerTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDuplicateHandlingStrategy() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DefaultBackupScopePartitioner(1, null, HashAlgorithm.NONE));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullHashAlgorithm() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DefaultBackupScopePartitioner(1, DuplicateHandlingStrategy.KEEP_EACH, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testPartitionBackupScopeShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var partitioner = new DefaultBackupScopePartitioner(1, DuplicateHandlingStrategy.KEEP_EACH, HashAlgorithm.NONE);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> partitioner.partitionBackupScope(null));

        //then + exception
    }

    @Test
    void testPartitionBackupScopeShouldReturnEmptyListWhenCalledWithEmptyList() {
        //given
        final var partitioner = new DefaultBackupScopePartitioner(1, DuplicateHandlingStrategy.KEEP_EACH, HashAlgorithm.NONE);

        //when
        final var actual = partitioner.partitionBackupScope(Collections.emptyList());

        //then
        Assertions.assertEquals(List.of(), actual);
    }

    @Test
    void testPartitionBackupScopeShouldReturnPartitionsWhenCalledWhileUsingKeepOnePerBackupStrategy() {
        //given
        final var partitioner = new DefaultBackupScopePartitioner(1, DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP, HashAlgorithm.SHA256);
        final var fileA1 = getFileMetadata("hash-a");
        final var fileA2 = getFileMetadata("hash-a");
        final var fileA3 = getFileMetadata("hash-a");
        final var fileB = getFileMetadata("hash-b");
        final var fileC = getFileMetadata("hash-c");
        final var scope = List.of(fileA1, fileB, fileC, fileA2, fileA3);

        //when
        final var actual = partitioner.partitionBackupScope(scope);

        //then
        Assertions.assertTrue(actual.contains(List.of(List.of(fileA1, fileA2, fileA3))),
                "A partition should contain fileA1, fileA2, fileA3");
        Assertions.assertTrue(actual.contains(List.of(List.of(fileB))),
                "A partition should contain fileB");
        Assertions.assertTrue(actual.contains(List.of(List.of(fileC))),
                "A partition should contain fileC");
    }

    private FileMetadata getFileMetadata(final String hash) {
        return FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(BackupPath.of(Path.of("test")))
                .originalHash(hash)
                .originalSizeBytes(1L)
                .group("test")
                .owner("test")
                .status(Change.NEW)
                .fileType(FileType.REGULAR_FILE)
                .posixPermissions("rw-rw-rw-")
                .createdUtcEpochSeconds(0L)
                .lastModifiedUtcEpochSeconds(0L)
                .lastAccessedUtcEpochSeconds(0L)
                .fileSystemKey("key")
                .build();
    }
}
