package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BackupSourceTest {

    private static Path testDataRoot;
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
    private static List<Path> dirsCreated;
    private static List<Path> filesCreated;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void beforeAll() {
        final String tempDir = System.getProperty("java.io.tmpdir");
        testDataRoot = Path.of(tempDir, "backup-source-" + UUID.randomUUID());
        dirsCreated = DIRS_RELATIVE.stream()
                .map(p -> Path.of(testDataRoot.toString() + File.separator + p).toFile())
                .map(f -> {
                    Assertions.assertTrue(f.mkdir(), "Directory was already found: " + f.getAbsolutePath());
                    f.deleteOnExit();
                    return f.toPath();
                })
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        filesCreated = FILES_RELATIVE.stream()
                .map(p -> Path.of(testDataRoot.toString() + File.separator + p).toFile())
                .map(f -> {
                    Assertions.assertDoesNotThrow(() -> Assertions
                            .assertTrue(f.createNewFile(), "File was already found: " + f.getAbsolutePath()));
                    f.deleteOnExit();
                    return f.toPath();
                })
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    @AfterAll
    static void afterAll() {
        Stream.of(filesCreated, dirsCreated)
                .flatMap(List::stream)
                .map(Path::toFile)
                .forEach(file -> Assertions.assertTrue(file.delete(), "Could not delete: " + file.getAbsolutePath()));
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

    @SuppressWarnings("checkstyle:MagicNumber")
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
        final BackupSource underTest = BackupSource.builder()
                .path(testDataRoot)
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final List<Path> actual = underTest.listMatchingFilePaths();

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
        final BackupSource underTest = BackupSource.builder()
                .path(testDataRoot)
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final List<Path> actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertEquals(expectedResults, actual.size());
        actual.forEach(path -> {
            Assertions.assertTrue(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
                    "File should be a directory but wasn't: " + path);
        });
    }

    @ParameterizedTest
    @MethodSource("nullFilterExpressionProvider")
    void testListMatchingFilePathsShouldUseDefaultFiltersWhenNullPatternSetIsSupplied(
            final Set<String> includePatterns,
            final Set<String> excludePatterns,
            final int expectedResults
    ) {
        //given
        final BackupSource underTest = BackupSource.builder()
                .path(testDataRoot)
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();

        //when
        final List<Path> actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertEquals(expectedResults, actual.size());
    }

    @Test
    void testListMatchingFilePathsShouldReturnSingleFileWhenRootIsRegularFile() {
        //given
        final Path expectedFile = Path.of(testDataRoot.toString(), ".hidden-file1.txt");
        final BackupSource underTest = BackupSource.builder()
                .path(expectedFile)
                .build();

        //when
        final List<Path> actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertIterableEquals(List.of(expectedFile), actual);
    }

    @Test
    void testListMatchingFilePathsShouldReturnNothingWhenRootDoesNotExist() {
        //given
        final Path expectedFile = Path.of(testDataRoot.toString(), "unknown-file.txt");
        final BackupSource underTest = BackupSource.builder()
                .path(expectedFile)
                .build();

        //when
        final List<Path> actual = underTest.listMatchingFilePaths();

        //then
        Assertions.assertIterableEquals(List.of(), actual);
    }

    @Test
    void testListMatchingFilePathsShouldThrowExceptionWhenIncludePatternsAreSuppliedAndRootIsRegularFile() {
        //given
        final Path expectedFile = Path.of(testDataRoot.toString(), "visible-file1.txt");
        final BackupSource underTest = BackupSource.builder()
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
        final Path expectedFile = Path.of(testDataRoot.toString(), "visible-file1.txt");
        final BackupSource underTest = BackupSource.builder()
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
        final BackupSource expected = BackupSource.builder()
                .path(testDataRoot)
                .includePatterns(Set.of("visible/**"))
                .excludePatterns(Set.of("**.txt"))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupSource actual = objectMapper.readerFor(BackupSource.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final BackupSource expected = BackupSource.builder()
                .path(Path.of(testDataRoot.toString(), "visible-file1.txt"))
                .build();
        final String json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupSource actual = objectMapper.readerFor(BackupSource.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }
}
