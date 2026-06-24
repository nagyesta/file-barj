package com.github.nagyesta.filebarj.core.persistence.h2.entity;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

class FileMetadataIndexTest {

    private static final FileMetadataIndex FILE_METADATA_INDEX = new FileMetadataIndex(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BackupPath.ofPathAsIs("/path"),
            "hash",
            1L,
            1L,
            FileType.REGULAR_FILE);

    public Stream<Arguments> equalsProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(FILE_METADATA_INDEX, FILE_METADATA_INDEX, true))
                .add(Arguments.of(
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path-2"),
                                "hash",
                                1L,
                                0L,
                                FileType.REGULAR_FILE
                        ), FILE_METADATA_INDEX, true))
                .add(Arguments.of(
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                "hash-2",
                                2L,
                                1L,
                                FileType.REGULAR_FILE
                        ), FILE_METADATA_INDEX, false))
                .add(Arguments.of(
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                null,
                                1L,
                                1L,
                                FileType.REGULAR_FILE
                        ),
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                null,
                                1L,
                                1L,
                                FileType.REGULAR_FILE
                        ), true))
                .add(Arguments.of(
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                null,
                                1L,
                                1L,
                                FileType.REGULAR_FILE
                        ),
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path-2"),
                                null,
                                1L,
                                0L,
                                FileType.REGULAR_FILE
                        ), false))
                .add(Arguments.of(
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                null,
                                1L,
                                1L,
                                FileType.REGULAR_FILE
                        ),
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                null,
                                1L,
                                1L,
                                FileType.SYMBOLIC_LINK
                        ), false))
                .add(Arguments.of(
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                "hash",
                                1L,
                                1L,
                                FileType.REGULAR_FILE
                        ),
                        new FileMetadataIndex(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BackupPath.ofPathAsIs("/path"),
                                "hash",
                                1L,
                                1L,
                                FileType.SYMBOLIC_LINK
                        ), false))
                .build();
    }

    @ParameterizedTest
    @MethodSource("equalsProvider")
    void testHashCodeShouldBeTheSameWhenEqualsReturnsTrue(
            final FileMetadataIndex a, final FileMetadataIndex b, final boolean expected) {
        //given

        //when
        final var actual = a.equals(b);
        final var hashCodeA = a.hashCode();
        final var hashCodeB = b.hashCode();

        //then
        Assertions.assertEquals(actual, expected);
        if (expected) {
            Assertions.assertEquals(hashCodeA, hashCodeB);
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testEqualsShouldReturnFalseWhenTheOtherIsNull() {
        //given

        //when
        final var actual = FILE_METADATA_INDEX.equals(null);

        //then
        Assertions.assertFalse(actual);
    }

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @Test
    void testEqualsShouldReturnFalseWhenTheOtherIsNotTheCorrectClass() {
        //given

        //when
        final var actual = FILE_METADATA_INDEX.equals("1");

        //then
        Assertions.assertFalse(actual);
    }
}
