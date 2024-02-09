package com.github.nagyesta.filebarj.core.config.enums;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashAlgorithmTest {

    public static final long NOW = Instant.now().getEpochSecond();
    public static final long A_MINUTE_AGO = NOW - 60;

    public static Stream<Arguments> sizeBasedGroupingProvider() {
        final var original = getRegularFileMetadata("file.png", 20, "hash1", NOW);
        final var duplicateDifferentName = getRegularFileMetadata("file2.png", 20, "hash1", NOW);
        final var differentSize = getRegularFileMetadata("file2.png", 21, "hash1", NOW);
        final var differentHash = getRegularFileMetadata("false/file.png", 20, "hash2", NOW);
        final var duplicateSubFolder = getRegularFileMetadata("sub/file.png", 20, "hash1", NOW);
        final var duplicateSubFolderDifferentHash = getRegularFileMetadata("diff/file.png", 20, "hash2", NOW);
        final var duplicateSubFolderDifferentTime = getRegularFileMetadata("time/file.png", 20, "hash1", A_MINUTE_AGO);
        return Stream.<Arguments>builder()
                .add(Arguments.of(original, original, true))
                .add(Arguments.of(original, duplicateDifferentName, false))
                .add(Arguments.of(original, differentSize, false))
                .add(Arguments.of(original, differentHash, true))
                .add(Arguments.of(original, duplicateSubFolder, true))
                .add(Arguments.of(original, duplicateSubFolderDifferentHash, true))
                .add(Arguments.of(original, duplicateSubFolderDifferentTime, false))
                .build();
    }

    public static Stream<Arguments> hashBasedGroupingProvider() {
        final var original = getRegularFileMetadata("file.png", 20, "hash1", NOW);
        final var duplicateDifferentName = getRegularFileMetadata("file2.png", 20, "hash1", NOW);
        final var differentSize = getRegularFileMetadata("file2.png", 21, "hash1", NOW);
        final var differentHash = getRegularFileMetadata("false/file.png", 20, "hash2", NOW);
        final var duplicateSubFolder = getRegularFileMetadata("sub/file.png", 20, "hash1", NOW);
        final var duplicateSubFolderDifferentHash = getRegularFileMetadata("diff/file.png", 20, "hash2", NOW);
        final var duplicateSubFolderDifferentTime = getRegularFileMetadata("time/file.png", 20, "hash1", A_MINUTE_AGO);
        return Stream.<Arguments>builder()
                .add(Arguments.of(original, original, true))
                .add(Arguments.of(original, duplicateDifferentName, true))
                .add(Arguments.of(original, differentSize, false))
                .add(Arguments.of(original, differentHash, false))
                .add(Arguments.of(original, duplicateSubFolder, true))
                .add(Arguments.of(original, duplicateSubFolderDifferentHash, false))
                .add(Arguments.of(original, duplicateSubFolderDifferentTime, true))
                .build();
    }

    @ParameterizedTest
    @MethodSource("sizeBasedGroupingProvider")
    void testFileGroupingFunctionShouldGenerateSameKeyWhenCalledOnNoneHashWithDuplicatesOfTheSameFile(
            final FileMetadata fileMetadata1, final FileMetadata fileMetadata2, final boolean expected) {
        //given
        final var underTest = HashAlgorithm.NONE;

        //when
        final var first = underTest.fileGroupingFunction().apply(fileMetadata1);
        final var second = underTest.fileGroupingFunction().apply(fileMetadata2);

        //then
        assertEquals(expected, first.equals(second));
    }

    @ParameterizedTest
    @MethodSource("hashBasedGroupingProvider")
    void testFileGroupingFunctionShouldGenerateSameKeyWhenCalledOnShaHashWithDuplicatesOfTheSameFile(
            final FileMetadata fileMetadata1, final FileMetadata fileMetadata2, final boolean expected) {
        //given
        final var underTest = HashAlgorithm.SHA256;

        //when
        final var first = underTest.fileGroupingFunction().apply(fileMetadata1);
        final var second = underTest.fileGroupingFunction().apply(fileMetadata2);

        //then
        assertEquals(expected, first.equals(second));
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
