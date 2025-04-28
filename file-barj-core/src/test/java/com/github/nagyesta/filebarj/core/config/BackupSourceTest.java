package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

class BackupSourceTest extends TempFileAwareTest {

    private static final List<String> DIRS_RELATIVE = List.of(
            "",
            ".hidden", ".hidden/dir1", ".hidden/dir2",
            "visible", "visible/dir1", "visible/dir2",
            "tmp", "tmp/ignored");
    private static final List<String> FILES_RELATIVE = List.of(
            ".hidden-file1.txt",
            "visible-file1.txt",
            ".hidden/file3.txt", ".hidden/dir1/1.txt", ".hidden/dir2/1.md",
            "visible/1.txt", "visible/dir1/1.txt",
            "tmp/1.txt");

    @Override
    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        DIRS_RELATIVE.stream()
                .map(p -> Path.of(testDataRoot.toString() + File.separator + p).toFile())
                .forEach(f -> {
                    //noinspection ResultOfMethodCallIgnored
                    f.mkdir();
                    Assertions.assertTrue(f.exists(), "Directory was already found: " + f.getAbsolutePath());
                    f.deleteOnExit();
                });
        FILES_RELATIVE.stream()
                .map(p -> Path.of(testDataRoot.toString() + File.separator + p).toFile())
                .forEach(f -> {
                    Assertions.assertDoesNotThrow(() -> Assertions
                            .assertTrue(f.createNewFile(), "File was already found: " + f.getAbsolutePath()));
                    f.deleteOnExit();
                });
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static Stream<Arguments> filterExpressionProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(Set.of("**/*.txt"), Set.of("**/dir2/**", "tmp", "tmp/**"), 9, ".txt"))
                .add(Arguments.of(Set.of("**/*.md"), Set.of(), 4, ".md"))
                .add(Arguments.of(Set.of(".hidden/**"), Set.of("**/*.md", "**/*.txt"), 4, "!!!NONE-MATCH!!!"))
                .add(Arguments.of(Set.of(".hidden*.txt"), Set.of(), 2, ".hidden-file1.txt"))
                .build();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static Stream<Arguments> emptyDirectoryFilterExpressionProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(Set.of("**/*.jpg"), Set.of(), 0))
                .add(Arguments.of(Set.of(), Set.of("**.txt", "**.md"), 9))
                .add(Arguments.of(Set.of("visible/**"), Set.of("**/*.txt", "**/*.md"), 4))
                .build();
    }

    @SuppressWarnings({"checkstyle:MagicNumber", "MagicNumber"})
    public static Stream<Arguments> nullFilterExpressionProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, null, 17))
                .add(Arguments.of(null, Set.of("**.txt"), 10))
                .add(Arguments.of(Set.of("visible/**"), null, 6))
                .build();
    }

    @ParameterizedTest
    @MethodSource("filterExpressionProvider")
    void testListMatchingFilePathsShouldOnlyReturnMatchingFilesAndTheirParentsWhenFilteringIsUsed(
            final Set<String> includePatterns,
            final Set<String> excludePatterns,
            final int expectedResults,
            final String expectedExtension
    ) {
        //given
        final var underTest = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final var actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertEquals(expectedResults, actual.size());
        actual.forEach(path -> {
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                Assertions.assertTrue(path.toString().endsWith(expectedExtension),
                        "File should be " + expectedExtension + " but found: " + path);
            } else {
                Assertions.assertTrue(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
                        "File should be a directory but wasn't: " + path);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("emptyDirectoryFilterExpressionProvider")
    void testListMatchingFilePathsShouldReturnEmptyDirectoriesWhenTheirChildrenAreFilteredOut(
            final Set<String> includePatterns,
            final Set<String> excludePatterns,
            final int expectedResults
    ) {
        //given
        final var underTest = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final var actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertEquals(expectedResults, actual.size());
        actual.forEach(path -> Assertions.assertTrue(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
                "File should be a directory but wasn't: " + path));
    }

    @ParameterizedTest
    @MethodSource("nullFilterExpressionProvider")
    void testListMatchingFilePathsShouldUseDefaultFiltersWhenNullPatternSetIsSupplied(
            final Set<String> includePatterns,
            final Set<String> excludePatterns,
            final int expectedResults
    ) {
        //given
        final var underTest = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final var actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertEquals(expectedResults, actual.size());
    }

    @Test
    void testListMatchingFilePathsShouldReturnSingleFileWhenRootIsRegularFile() {
        //given
        final var expectedFile = Path.of(testDataRoot.toString(), ".hidden-file1.txt");
        final var underTest = BackupSource.builder()
                .path(BackupPath.of(expectedFile))
                .build();

        //when
        final var actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertIterableEquals(List.of(expectedFile), actual);
    }

    @Test
    void testListMatchingFilePathsShouldReturnNothingWhenRootDoesNotExist() {
        //given
        final var expectedFile = BackupPath.of(testDataRoot, "unknown-file.txt");
        final var underTest = BackupSource.builder()
                .path(expectedFile)
                .build();

        //when
        final var actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertIterableEquals(List.of(), actual);
    }

    @Test
    void testListMatchingFilePathsShouldThrowExceptionWhenIncludePatternsAreSuppliedAndRootIsRegularFile() {
        //given
        final var expectedFile = BackupPath.of(testDataRoot, "visible-file1.txt");
        final var underTest = BackupSource.builder()
                .path(expectedFile)
                .includePatterns(Set.of("**.txt"))
                .build();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, underTest::listMatchingFilePaths);

        //then + exception
    }

    @Test
    void testListMatchingFilePathsShouldThrowExceptionWhenExcludePatternsAreSuppliedAndRootIsRegularFile() {
        //given
        final var expectedFile = BackupPath.of(testDataRoot, "visible-file1.txt");
        final var underTest = BackupSource.builder()
                .path(expectedFile)
                .excludePatterns(Set.of("**.txt"))
                .build();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, underTest::listMatchingFilePaths);

        //then + exception
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final var expected = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .includePatterns(Set.of("visible/**"))
                .excludePatterns(Set.of("**.txt"))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupSource actual = objectMapper.readerFor(BackupSource.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final var expected = BackupSource.builder()
                .path(BackupPath.of(testDataRoot, "visible-file1.txt"))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupSource actual = objectMapper.readerFor(BackupSource.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @ParameterizedTest
    @MethodSource("filterExpressionProvider")
    void testToStringShouldContainRootAndPatternsWhenCalled(
            final Set<String> includePatterns,
            final Set<String> excludePatterns
    ) {
        //given
        final var underTest = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final var actual = underTest.toString();

        //then
        Assertions.assertTrue(actual.contains(FilenameUtils.separatorsToUnix(testDataRoot.toString())),
                "Root should be contained in: " + actual);
        Assertions.assertTrue(actual.contains(FilenameUtils.separatorsToUnix(excludePatterns.toString())),
                "Exclude patterns should be contained in: " + actual);
        Assertions.assertTrue(actual.contains(FilenameUtils.separatorsToUnix(includePatterns.toString())),
                "Include patterns should be contained in: " + actual);
    }
}
