package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;

class BackupPathTest extends TempFileAwareTest {

    public static Stream<Arguments> fileUriProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(URI.create("file:///"), "/"))
                .add(Arguments.of(URI.create("file:///test"), "/test"))
                .add(Arguments.of(URI.create("file:///D:/test"), "D:/test"))
                .add(Arguments.of(URI.create("file:/D:/test"), "D:/test"))
                .build();
    }

    public static Stream<Arguments> comparisonProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(URI.create("file:///"), URI.create("file:///"), 0))
                .add(Arguments.of(URI.create("file:///test"), URI.create("file:///test"), 0))
                .add(Arguments.of(URI.create("file:///test"), URI.create("file:///test//"), 0))
                .add(Arguments.of(URI.create("file:///b"), URI.create("file:///a"), 1))
                .add(Arguments.of(URI.create("file:///test"), URI.create("file:///test2"), -1))
                .add(Arguments.of(URI.create("file:///a"), URI.create("file:///b"), -1))
                .build();
    }

    public static Stream<Arguments> startsWithProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(URI.create("file:///"), URI.create("file:///"), true))
                .add(Arguments.of(URI.create("file:///test"), URI.create("file:///test"), false))
                .add(Arguments.of(URI.create("file:///test/path"), URI.create("file:///test"), true))
                .add(Arguments.of(URI.create("file:///b"), URI.create("file:///a"), false))
                .add(Arguments.of(URI.create("file:///D:/test"), URI.create("file:///D:/"), true))
                .add(Arguments.of(URI.create("file:///D:/test"), URI.create("file:///test"), false))
                .build();
    }

    @ParameterizedTest
    @MethodSource("fileUriProvider")
    void testFromUriShouldRemoveFileSchemeWhenCalledWithValidUri(final URI input, final String expected) {
        //given
        final var uri = input.toString();

        //when
        final var actual = BackupPath.fromUri(uri);

        //then
        Assertions.assertEquals(expected, actual.toString());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFromUriShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BackupPath.fromUri(null));

        //then + exception
    }

    @Test
    void testFromUriShouldThrowExceptionWhenCalledWithInvalidUri() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BackupPath.fromUri("relativeUri"));

        //then + exception
    }

    @ParameterizedTest
    @MethodSource("fileUriProvider")
    void testToUriShouldRemoveFileSchemeWhenCalledWithValidUri(final URI expected) {
        //given
        final var underTest = BackupPath.fromUri(expected.toString());

        //when
        final var actual = underTest.toUri();

        //then
        Assertions.assertEquals(expected, URI.create(actual));
    }

    @Test
    void testOfWithPathShouldCreateAbsolutePathWhenCalledWithValidPath() {
        //given

        //when
        final var underTest = BackupPath.of(testDataRoot);
        final var actual = underTest.toString();

        //then
        final var expected = FilenameUtils.separatorsToUnix(testDataRoot.toAbsolutePath().toString());
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testOfWithPathShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BackupPath.of(null));

        //then + exception
    }

    @Test
    void testOfWithOneStringShouldCreateAbsolutePathWhenCalledWithValidPath() {
        //given

        //when
        final var underTest = BackupPath.of(testDataRoot);
        final var actual = underTest.toString();

        //then
        final var expected = FilenameUtils.separatorsToUnix(testDataRoot.toAbsolutePath().toString());
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testOfWithOneStringShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BackupPath.ofPathAsIs(null));

        //then + exception
    }

    @Test
    void testOfWithOneTwoStringsShouldCreateAbsolutePathWhenCalledWithValidPath() {
        //given
        final var directory = "directory";

        //when
        final var underTest = BackupPath.of(testDataRoot, directory);
        final var actual = underTest.toString();

        //then
        final var expected = FilenameUtils.separatorsToUnix(testDataRoot.resolve(directory).toAbsolutePath().toString());
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testOfWithTwoStringShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BackupPath.of(testDataRoot, null));

        //then + exception
    }

    @Test
    void testOfWithOneThreeStringsShouldCreateAbsolutePathWhenCalledWithValidPath() {
        //given
        final var directory = "directory";
        final var path = "path.txt";

        //when
        final var underTest = BackupPath.of(testDataRoot, directory, path);
        final var actual = underTest.toString();

        //then
        final var expected = FilenameUtils.separatorsToUnix(testDataRoot.resolve(directory).resolve(path).toAbsolutePath().toString());
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testOfWithThreeStringShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var directory = "directory";

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> BackupPath.of(testDataRoot, directory, null));

        //then + exception
    }

    @Test
    void testToOsPathShouldReturnUnchangedPathWhenCalledWithRelativePath() {
        //given
        final var underTest = BackupPath.ofPathAsIs("test");

        //when
        final var actual = underTest.toOsPath();

        //then
        final var expected = Path.of("test").toString();
        Assertions.assertEquals(expected, actual.toString());
    }

    @Test
    void testToOsPathShouldReturnUnchangedPathWhenCalledWithAbsolutePath() {
        //given
        final var expected = Path.of("test").toAbsolutePath().toString();
        final var underTest = BackupPath.ofPathAsIs(expected);

        //when
        final var actual = underTest.toOsPath();

        //then
        Assertions.assertEquals(expected, actual.toString());
    }

    @ParameterizedTest
    @MethodSource("comparisonProvider")
    void testCompareToShouldCompareByPath(final URI leftUri, final URI rightUri, final int expected) {
        //given
        final var left = BackupPath.fromUri(leftUri.toString());
        final var right = BackupPath.fromUri(rightUri.toString());

        //when
        final var actual = left.compareTo(right);

        //then
        Assertions.assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("startsWithProvider")
    void testStartsWithShouldReturnTrueOnlyWhenCalledWithAPrefix(final URI leftUri, final URI rightUri, final boolean expected) {
        //given
        final var left = BackupPath.fromUri(leftUri.toString());
        final var right = BackupPath.fromUri(rightUri.toString());

        //when
        final var actual = left.startsWith(right);

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testGetFileNameShouldReturnFileNameWhenCalled() {
        //given
        final var fileName = "test.txt";
        final var underTest = BackupPath.of(testDataRoot.resolve(fileName));

        //when
        final var actual = underTest.getFileName();

        //then
        Assertions.assertEquals(fileName, actual);
    }

    @Test
    void testGetParentShouldReturnParentWhenParentExists() {
        //given
        final var child = testDataRoot.resolve("test.txt");
        final var underTest = BackupPath.of(child);
        final var expected = BackupPath.of(testDataRoot);

        //when
        final var actual = underTest.getParent();

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testGetParentShouldReturnNullWhenParentDoesNotExists() {
        //given
        final var underTest = BackupPath.ofPathAsIs("/");

        //when
        final var actual = underTest.getParent();

        //then
        Assertions.assertNull(actual);
    }

    @Test
    void testToFileShouldReturnFileWhenCalled() {
        //given
        final var underTest = BackupPath.of(testDataRoot);

        //when
        final var actual = underTest.toFile();

        //then
        Assertions.assertEquals(testDataRoot.toFile(), actual);
    }

    @Test
    void testResolveShouldReturnChildWhenCalled() {
        //given
        final var fileName = "test.txt";
        final var child = testDataRoot.resolve(fileName);
        final var underTest = BackupPath.of(testDataRoot);
        final var expected = BackupPath.of(child);

        //when
        final var actual = underTest.resolve(fileName);

        //then
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testResolveShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = BackupPath.of(testDataRoot);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.resolve(null));

        //then + exception
    }
}
