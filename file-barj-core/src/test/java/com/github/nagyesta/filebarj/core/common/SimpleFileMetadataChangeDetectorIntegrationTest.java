package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.restore.worker.FileMetadataSetterLocal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

class SimpleFileMetadataChangeDetectorIntegrationTest extends AbstractFileMetadataChangeDetectorIntegrationTest {

    public static Stream<Arguments> fileContentProvider() {
        return Stream.of(commonFileContentProvider(), Stream.<Arguments>builder()
                .add(Arguments.of(
                        "recreate.txt",
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true,
                        "a.txt", FileType.SYMBOLIC_LINK, "rwxrwxrwx",
                        true, false, Change.CONTENT_CHANGED))
                .build()).flatMap(Function.identity());
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @ParameterizedTest
    @MethodSource("fileContentProvider")
    void testSimpleChangeDetectorShouldDetectChangesWhenCalled(
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
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

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
    void testSimpleChangeDetectorShouldDetectChangesWhenContentWasRolledBack()
            throws IOException, InterruptedException {
        //given
        final var orig = createMetadata("file.txt", "content-1", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var prev = createMetadata("file.txt", "content-2", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var curr = createMetadata("file.txt", "content-1", FileType.REGULAR_FILE, "rwxrwxrwx", true);
        //reset the lat modified timestamp to simulate a restore
        new FileMetadataSetterLocal(new RestoreTargets(Set.of(new RestoreTarget(testDataRoot, testDataRoot)))).setTimestamps(orig);
        final var restored = PARSER.parse(curr.getAbsolutePath().toFile(), CONFIGURATION);
        final var manifests = Map.of("1", Map.of(orig.getId(), orig), "2", Map.of(prev.getId(), prev));
        final var underTest = new SimpleFileMetadataChangeDetector(CONFIGURATION, manifests);

        //when
        final var relevant = underTest.findMostRelevantPreviousVersion(restored);
        Assertions.assertNotNull(relevant);
        final var actualMetadataChanged = underTest.hasMetadataChanged(relevant, restored);
        final var actualContentChanged = underTest.hasContentChanged(relevant, restored);
        final var actualChange = underTest.classifyChange(relevant, restored);

        //then
        Assertions.assertTrue(actualMetadataChanged);
        Assertions.assertFalse(actualContentChanged);
        Assertions.assertEquals(Change.ROLLED_BACK, actualChange);
    }

    @Test
    void testFindMostRelevantPreviousVersionByContentShouldFallbackToFilePathWhenContentWasNotMatching()
            throws IOException, InterruptedException {
        //given
        final var orig = createMetadata("file.txt", "content-1", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var prev = createMetadata("file.txt", "content-2", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        waitASecond();
        final var curr = createMetadata("file.txt", "content-3", FileType.REGULAR_FILE, "rwxrwxrwx", true);
        final var manifests = Map.of("1", Map.of(orig.getId(), orig), "2", Map.of(prev.getId(), prev));
        final var underTest = new SimpleFileMetadataChangeDetector(CONFIGURATION, manifests);

        //when
        final var actual = underTest.findMostRelevantPreviousVersion(curr);

        //then
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(actual.getId(), prev.getId());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFindMostRelevantPreviousVersionByContentShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.findMostRelevantPreviousVersion(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFindPreviousVersionByAbsolutePathShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.findPreviousVersionByAbsolutePath(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testIsFromLastIncrementShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.isFromLastIncrement(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testHasMetadataChangedShouldThrowExceptionWhenCalledWithNullCurrentFile()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasMetadataChanged(prev, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testHasMetadataChangedShouldThrowExceptionWhenCalledWithNullPreviousFile()
            throws IOException {
        //given
        final var curr = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(curr);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasMetadataChanged(null, curr));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testHasContentChangedShouldThrowExceptionWhenCalledWithNullCurrentFile()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasContentChanged(prev, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testHasContentChangedShouldThrowExceptionWhenCalledWithNullPreviousFile()
            throws IOException {
        //given
        final var curr = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(curr);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.hasContentChanged(null, curr));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testClassifyChangeShouldThrowExceptionWhenCalledWithNullCurrentFile()
            throws IOException {
        //given
        final var prev = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(prev);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.classifyChange(prev, null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testClassifyChangeShouldThrowExceptionWhenCalledWithNullPreviousFile()
            throws IOException {
        //given
        final var curr = createMetadata("file.txt", "content", FileType.REGULAR_FILE, "rw-rw-rw-", true);
        final var underTest = getDefaultSimpleFileMetadataChangeDetector(curr);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.classifyChange(null, curr));

        //then + exception
    }
}
