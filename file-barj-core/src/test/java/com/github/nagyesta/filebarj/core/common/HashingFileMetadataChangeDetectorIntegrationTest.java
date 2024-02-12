package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

class HashingFileMetadataChangeDetectorIntegrationTest extends AbstractFileMetadataChangeDetectorIntegrationTest {

    public static Stream<Arguments> fileContentProvider() {
        return Stream.of(commonFileContentProvider(), Stream.<Arguments>builder()
                .add(Arguments.of(
                        "recreate.txt",
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true,
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        false, false, Change.NO_CHANGE))
                .build()).flatMap(Function.identity());
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @ParameterizedTest
    @MethodSource("fileContentProvider")
    @DisabledOnOs(WINDOWS)
    void testHashingChangeDetectorShouldDetectChangesWhenCalled(
            final String name,
            final String prevContent, final FileType prevType, final String prevPermission,
            final boolean recreate,
            final String currContent, final FileType currType, final String currPermission,
            final boolean expectedContentChanged, final boolean expectedMetadataChanged, final Change expectedChange)
            throws IOException, InterruptedException {
        //given
        final var prev = createMetadata(name, prevContent, prevType, prevPermission, true);
        waitASecond();
        final var curr = createMetadata(name, currContent, currType, currPermission, recreate);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        final var actualMetadataChanged = underTest.hasMetadataChanged(prev, curr);
        final var actualContentChanged = underTest.hasContentChanged(prev, curr);
        final var actualChange = underTest.classifyChange(prev, curr);

        //then
        Assertions.assertEquals(expectedMetadataChanged, actualMetadataChanged);
        Assertions.assertEquals(expectedContentChanged, actualContentChanged);
        Assertions.assertEquals(expectedChange, actualChange);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHashingChangeDetectorShouldDetectChangesWhenContentWasRolledBack()
            throws IOException, InterruptedException {
        //given
        final var orig = createMetadata("file.txt", "content-1", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var prev = createMetadata("file.txt", "content-2", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var curr = createMetadata("file.txt", "content-1", FileType.REGULAR_FILE, "rwxrwxrwx", true);
        final var manifests = Map.of("1", Map.of(orig.getId(), orig), "2", Map.of(prev.getId(), prev));
        final var underTest = new HashingFileMetadataChangeDetector(manifests);

        //when
        final var relevant = underTest.findMostRelevantPreviousVersion(curr);
        Assertions.assertNotNull(relevant);
        final var actualMetadataChanged = underTest.hasMetadataChanged(relevant, curr);
        final var actualContentChanged = underTest.hasContentChanged(relevant, curr);
        final var actualChange = underTest.classifyChange(relevant, curr);

        //then
        Assertions.assertTrue(actualMetadataChanged);
        Assertions.assertFalse(actualContentChanged);
        Assertions.assertEquals(Change.ROLLED_BACK, actualChange);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testFindMostRelevantPreviousVersionByContentShouldFallbackToFilePathWhenContentWasNotMatching()
            throws IOException, InterruptedException {
        //given
        final var orig = createMetadata("file.txt", "content-1", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var prev = createMetadata("file.txt", "content-2", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var curr = createMetadata("file.txt", "content-3", FileType.REGULAR_FILE, "rwxrwxrwx", true);
        final var manifests = Map.of("1", Map.of(orig.getId(), orig), "2", Map.of(prev.getId(), prev));
        final var underTest = new HashingFileMetadataChangeDetector(manifests);

        //when
        final var actual = underTest.findMostRelevantPreviousVersion(curr);

        //then
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(actual.getId(), prev.getId());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testFindMostRelevantPreviousVersionByContentShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.findMostRelevantPreviousVersion(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testFindPreviousVersionByAbsolutePathShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.findPreviousVersionByAbsolutePath(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testIsFromLastIncrementShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.isFromLastIncrement(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testHasMetadataChangedShouldThrowExceptionWhenCalledWithNullCurrentFile()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasMetadataChanged(prev, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testHasMetadataChangedShouldThrowExceptionWhenCalledWithNullPreviousFile()
            throws IOException {
        //given
        final var curr = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(curr);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasMetadataChanged(null, curr));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testHasContentChangedShouldThrowExceptionWhenCalledWithNullCurrentFile()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasContentChanged(prev, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testHasContentChangedShouldThrowExceptionWhenCalledWithNullPreviousFile()
            throws IOException {
        //given
        final var curr = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(curr);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasContentChanged(null, curr));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testClassifyChangeShouldThrowExceptionWhenCalledWithNullCurrentFile()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.classifyChange(prev, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisabledOnOs(WINDOWS)
    void testClassifyChangeShouldThrowExceptionWhenCalledWithNullPreviousFile()
            throws IOException {
        //given
        final var curr = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultHashingFileMetadataChangeDetector(curr);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.classifyChange(null, curr));

        //then + exception
    }

}
