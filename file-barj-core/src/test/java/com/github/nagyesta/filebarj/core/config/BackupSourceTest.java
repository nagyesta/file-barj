package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.common.BackupSourceScanner;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFilePathSetRepository;
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

    private static final String ROOT = "";
    private static final String HIDDEN = ".hidden";
    private static final String HIDDEN_DIR_1 = ".hidden/dir1";
    private static final String HIDDEN_DIR_2 = ".hidden/dir2";
    private static final String VISIBLE = "visible";
    private static final String VISIBLE_DIR_1 = "visible/dir1";
    private static final String VISIBLE_DIR_2 = "visible/dir2";
    private static final String TMP = "tmp";
    private static final String TMP_IGNORED = "tmp/ignored";
    private static final String HIDDEN_FILE_1_TXT = ".hidden-file1.txt";
    private static final String VISIBLE_FILE_1_TXT = "visible-file1.txt";
    private static final String HIDDEN_FILE_3_TXT = ".hidden/file3.txt";
    private static final String HIDDEN_DIR_1_1_TXT = ".hidden/dir1/1.txt";
    private static final String HIDDEN_DIR_2_1_MD = ".hidden/dir2/1.md";
    private static final String VISIBLE_1_TXT = "visible/1.txt";
    private static final String VISIBLE_DIR_1_1_TXT = "visible/dir1/1.txt";
    private static final String TMP_1_TXT = "tmp/1.txt";
    private static final List<String> DIRS_RELATIVE = List.of(
            ROOT,
            HIDDEN, HIDDEN_DIR_1, HIDDEN_DIR_2,
            VISIBLE, VISIBLE_DIR_1, VISIBLE_DIR_2,
            TMP, TMP_IGNORED);
    private static final List<String> FILES_RELATIVE = List.of(
            HIDDEN_FILE_1_TXT,
            VISIBLE_FILE_1_TXT,
            HIDDEN_FILE_3_TXT, HIDDEN_DIR_1_1_TXT, HIDDEN_DIR_2_1_MD,
            VISIBLE_1_TXT, VISIBLE_DIR_1_1_TXT,
            TMP_1_TXT);

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
                .add(Arguments.of(Set.of("**/*.txt"), Set.of("**/dir2/**", TMP, "tmp/**"),
                        List.of(ROOT, HIDDEN, HIDDEN_DIR_1, VISIBLE, VISIBLE_DIR_1,
                                HIDDEN_FILE_3_TXT, HIDDEN_DIR_1_1_TXT,
                                VISIBLE_1_TXT, VISIBLE_DIR_1_1_TXT
                        )
                ))
                .add(Arguments.of(Set.of("**/*.md"), Set.of(),
                        List.of(ROOT, HIDDEN, HIDDEN_DIR_2, HIDDEN_DIR_2_1_MD)
                ))
                .add(Arguments.of(Set.of(".[hH]idden/**"), Set.of("**/*.md", "**/*.txt"),
                        List.of(ROOT, HIDDEN, HIDDEN_DIR_1, HIDDEN_DIR_2)
                ))
                .add(Arguments.of(Set.of(".?idden*.txt"), Set.of(),
                        List.of(ROOT, HIDDEN_FILE_1_TXT)
                ))
                .add(Arguments.of(Set.of(".hidden-file[0-9].txt"), Set.of(),
                        List.of(ROOT, HIDDEN_FILE_1_TXT)
                ))
                .add(Arguments.of(Set.of(".hidden-file[!a-z].txt"), Set.of(),
                        List.of(ROOT, HIDDEN_FILE_1_TXT)
                ))
                .add(Arguments.of(Set.of("???????-file1.txt"), Set.of(),
                        List.of(ROOT, HIDDEN_FILE_1_TXT, VISIBLE_FILE_1_TXT)
                ))
                .add(Arguments.of(Set.of("[.v][hi][is][di][db][el][ne]-file1.txt"), Set.of(),
                        List.of(ROOT, HIDDEN_FILE_1_TXT, VISIBLE_FILE_1_TXT)
                ))
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

    public static Stream<Arguments> nullFilterExpressionProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, null,
                        List.of(ROOT, HIDDEN, HIDDEN_DIR_1, HIDDEN_DIR_2, VISIBLE, VISIBLE_DIR_1, VISIBLE_DIR_2, TMP, TMP_IGNORED,
                                HIDDEN_FILE_1_TXT, VISIBLE_FILE_1_TXT, HIDDEN_FILE_3_TXT, HIDDEN_DIR_1_1_TXT, HIDDEN_DIR_2_1_MD,
                                VISIBLE_1_TXT, VISIBLE_DIR_1_1_TXT, TMP_1_TXT
                        )
                ))
                .add(Arguments.of(null, Set.of("**.txt"),
                        List.of(ROOT, HIDDEN, HIDDEN_DIR_1, HIDDEN_DIR_2, VISIBLE, VISIBLE_DIR_1, VISIBLE_DIR_2, TMP, TMP_IGNORED,
                                HIDDEN_DIR_2_1_MD
                        )
                ))
                .add(Arguments.of(Set.of("visible/**"), null,
                        List.of(ROOT, VISIBLE, VISIBLE_DIR_1, VISIBLE_DIR_2,
                                VISIBLE_1_TXT, VISIBLE_DIR_1_1_TXT
                        )
                ))
                .add(Arguments.of(null, Set.of(VISIBLE),
                        List.of(ROOT, HIDDEN, HIDDEN_DIR_1, HIDDEN_DIR_2, TMP, TMP_IGNORED,
                                HIDDEN_FILE_1_TXT, VISIBLE_FILE_1_TXT, HIDDEN_FILE_3_TXT, HIDDEN_DIR_1_1_TXT, HIDDEN_DIR_2_1_MD, TMP_1_TXT
                        )
                ))
                .build();
    }

    @ParameterizedTest
    @MethodSource("filterExpressionProvider")
    void testListMatchingFilePathsShouldOnlyReturnMatchingFilesAndTheirParentsWhenFilteringIsUsed(
            final Set<String> includePatterns,
            final Set<String> excludePatterns,
            final List<String> expectedPaths
    ) {
        //given
        final var source = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();
        final var underTest = new BackupSourceScanner(fileSetRepository, source);

        //when
        final var actualId = fileSetRepository.createFileSet();
        underTest.listMatchingFilePaths(actualId);
        final var actual = fileSetRepository.findAll(actualId, 0, Integer.MAX_VALUE);

        //then
        assertExpectedPathsFound(expectedPaths, actual);
    }

    @ParameterizedTest
    @MethodSource("emptyDirectoryFilterExpressionProvider")
    void testListMatchingFilePathsShouldReturnEmptyDirectoriesWhenTheirChildrenAreFilteredOut(
            final Set<String> includePatterns,
            final Set<String> excludePatterns,
            final int expectedResults
    ) {
        //given
        final var source = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();
        final var underTest = new BackupSourceScanner(fileSetRepository, source);

        //when
        final var actualId = fileSetRepository.createFileSet();
        underTest.listMatchingFilePaths(actualId);
        final var actual = fileSetRepository.findAll(actualId, 0, Integer.MAX_VALUE);

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
            final List<String> expectedPaths
    ) {
        //given
        final var source = BackupSource.builder()
                .path(BackupPath.of(testDataRoot))
                .excludePatterns(excludePatterns)
                .includePatterns(includePatterns)
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();
        final var underTest = new BackupSourceScanner(fileSetRepository, source);

        //when
        final var actualId = fileSetRepository.createFileSet();
        underTest.listMatchingFilePaths(actualId);
        final var actual = fileSetRepository.findAll(actualId, 0, Integer.MAX_VALUE);

        //then
        assertExpectedPathsFound(expectedPaths, actual);
    }

    @Test
    void testListMatchingFilePathsShouldReturnSingleFileWhenRootIsRegularFile() {
        //given
        final var expectedFile = testDataRoot.resolve(HIDDEN_FILE_1_TXT);
        final var source = BackupSource.builder()
                .path(BackupPath.of(expectedFile))
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();
        final var underTest = new BackupSourceScanner(fileSetRepository, source);

        //when
        final var actualId = fileSetRepository.createFileSet();
        underTest.listMatchingFilePaths(actualId);
        final var actual = fileSetRepository.findAll(actualId, 0, Integer.MAX_VALUE);

        //then
        Assertions.assertIterableEquals(List.of(expectedFile), actual);
    }

    @Test
    void testListMatchingFilePathsShouldReturnNothingWhenRootDoesNotExist() {
        //given
        final var expectedFile = BackupPath.of(testDataRoot, "unknown-file.txt");
        final var source = BackupSource.builder()
                .path(expectedFile)
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();
        final var underTest = new BackupSourceScanner(fileSetRepository, source);

        //when
        final var actualId = fileSetRepository.createFileSet();
        underTest.listMatchingFilePaths(actualId);
        final var actual = fileSetRepository.findAll(actualId, 0, Integer.MAX_VALUE);

        //then
        Assertions.assertIterableEquals(List.of(), actual);
    }

    @Test
    void testBuildShouldThrowExceptionWhenIncludePatternsAreSuppliedAndRootIsRegularFile() {
        //given
        final var expectedFile = BackupPath.of(testDataRoot, VISIBLE_FILE_1_TXT);
        final var source = BackupSource.builder()
                .path(expectedFile)
                .includePatterns(Set.of("**.txt"))
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new BackupSourceScanner(fileSetRepository, source));

        //then + exception
    }

    @Test
    void testBuildShouldThrowExceptionWhenExcludePatternsAreSuppliedAndRootIsRegularFile() {
        //given
        final var expectedFile = BackupPath.of(testDataRoot, VISIBLE_FILE_1_TXT);
        final var source = BackupSource.builder()
                .path(expectedFile)
                .excludePatterns(Set.of("**.txt"))
                .build();
        final var fileSetRepository = new InMemoryFilePathSetRepository();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new BackupSourceScanner(fileSetRepository, source));

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
                .path(BackupPath.of(testDataRoot, VISIBLE_FILE_1_TXT))
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

    private void assertExpectedPathsFound(
            final List<String> expectedPaths,
            final List<Path> actual) {
        final var expected = expectedPaths.stream()
                .map(path -> testDataRoot.resolve(path))
                .sorted()
                .toList();
        Assertions.assertIterableEquals(expected, actual);
    }
}
