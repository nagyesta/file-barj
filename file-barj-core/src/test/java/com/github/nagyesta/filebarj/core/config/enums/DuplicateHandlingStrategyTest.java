package com.github.nagyesta.filebarj.core.config.enums;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy.KEEP_EACH;
import static com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP;
import static com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm.NONE;
import static com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm.SHA256;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DuplicateHandlingStrategyTest extends TempFileAwareTest {

    public static final long NOW = Instant.now().getEpochSecond();
    public static final long A_MINUTE_AGO = NOW - 60;

    public static Stream<Arguments> groupingProvider() {
        final var original = getRegularFileMetadata("file.png", 20, "hash1", NOW);
        final var duplicateDifferentName = getRegularFileMetadata("file2.png", 20, "hash1", NOW);
        final var differentSize = getRegularFileMetadata("file2.png", 21, "hash1", NOW);
        final var differentHash = getRegularFileMetadata("false/file.png", 20, "hash2", NOW);
        final var duplicateSubFolder = getRegularFileMetadata("sub/file.png", 20, "hash1", NOW);
        final var duplicateSubFolderDifferentHash = getRegularFileMetadata("diff/file.png", 20, "hash2", NOW);
        final var duplicateSubFolderDifferentTime = getRegularFileMetadata("time/file.png", 20, "hash1", A_MINUTE_AGO);
        return Stream.<Arguments>builder()
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, original, SHA256, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateDifferentName, SHA256, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, differentSize, SHA256, false))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, differentHash, SHA256, false))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateSubFolder, SHA256, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateSubFolderDifferentHash, SHA256, false))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateSubFolderDifferentTime, SHA256, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, original, NONE, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateDifferentName, NONE, false))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, differentSize, NONE, false))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, differentHash, NONE, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateSubFolder, NONE, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateSubFolderDifferentHash, NONE, true))
                .add(Arguments.of(KEEP_ONE_PER_BACKUP, original, duplicateSubFolderDifferentTime, NONE, false))
                .add(Arguments.of(KEEP_EACH, original, original, SHA256, true))
                .add(Arguments.of(KEEP_EACH, original, duplicateDifferentName, SHA256, false))
                .add(Arguments.of(KEEP_EACH, original, differentSize, SHA256, false))
                .add(Arguments.of(KEEP_EACH, original, differentHash, SHA256, false))
                .add(Arguments.of(KEEP_EACH, original, duplicateSubFolder, SHA256, false))
                .add(Arguments.of(KEEP_EACH, original, duplicateSubFolderDifferentHash, SHA256, false))
                .add(Arguments.of(KEEP_EACH, original, duplicateSubFolderDifferentTime, SHA256, false))
                .build();
    }

    @ParameterizedTest
    @MethodSource("groupingProvider")
    void testFileGroupingFunctionForHashShouldGenerateSameKeyWhenCalledWithDuplicatesOfTheSameFile(
            final DuplicateHandlingStrategy underTest,
            final FileMetadata fileMetadata1, final FileMetadata fileMetadata2,
            final HashAlgorithm hashAlgorithm, final boolean expected) {
        //given

        //when
        final var first = underTest.fileGroupingFunctionForHash(hashAlgorithm).apply(fileMetadata1);
        final var second = underTest.fileGroupingFunctionForHash(hashAlgorithm).apply(fileMetadata2);

        //then
        assertEquals(expected, first.equals(second));
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "DataFlowIssue"})
    @Test
    void testFileGroupingFunctionForHashOfKeepEachShouldThrowExceptionWhenCalledWithNullHash() {
        //given
        final var underTest = DuplicateHandlingStrategy.KEEP_EACH;

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.fileGroupingFunctionForHash(null));

        //then + exception
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "DataFlowIssue"})
    @Test
    void testFileGroupingFunctionForHashOfKeepOnePerBackupShouldThrowExceptionWhenCalledWithNullHash() {
        //given
        final var underTest = DuplicateHandlingStrategy.KEEP_ONE_PER_BACKUP;

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.fileGroupingFunctionForHash(null));

        //then + exception
    }

    private static FileMetadata getRegularFileMetadata(
            final String name, final long size, final String hash, final long lastModified) {
        return FileMetadata.builder()
                .absolutePath(BackupPath.of(Path.of(name)))
                .fileType(FileType.REGULAR_FILE)
                .id(UUID.randomUUID())
                .originalHash(hash)
                .originalSizeBytes(size)
                .lastModifiedUtcEpochSeconds(lastModified)
                .createdUtcEpochSeconds(Instant.now().minusSeconds(1).getEpochSecond())
                .lastAccessedUtcEpochSeconds(NOW)
                .posixPermissions("rwxrwxrwx")
                .owner("owner")
                .group("group")
                .status(Change.NEW)
                .build();
    }
}
