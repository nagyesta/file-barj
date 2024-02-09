package com.github.nagyesta.filebarj.io.stream.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

class MergingFileInputStreamTest {

    public static Stream<Arguments> nullProvider() {
        final var folder = getResourcesFolder();
        final var prefix = "a-";
        final var extension = ".txt";
        return Stream.<Arguments>builder()
                .add(Arguments.of(folder, null, null))
                .add(Arguments.of(null, prefix, null))
                .add(Arguments.of(null, null, extension))
                .add(Arguments.of(null, prefix, extension))
                .add(Arguments.of(folder, null, extension))
                .add(Arguments.of(folder, prefix, null))
                .build();
    }

    @SuppressWarnings("resource")
    @ParameterizedTest
    @MethodSource("nullProvider")
    void testConstructorShouldThrowExceptionWhenCalledWithNullFileNameTokens(
            final Path directory, final String prefix, final String extension) {
        //given

        //when
        Assertions.assertThrows(NullPointerException.class, () -> new MergingFileInputStream(directory, prefix, extension));

        //then + exception
    }

    @SuppressWarnings({"DataFlowIssue", "resource"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullStreamList() {
        //given

        //when
        Assertions.assertThrows(NullPointerException.class, () -> new MergingFileInputStream(null));

        //then + exception
    }

    @Test
    void testReadAllBytesShouldReadAllBytesAcrossTheChunksWhenCalledOnAListOfStreams() throws IOException {
        //given
        final var allFiles = fileList();
        final var expectedBytes = getInputStream().readAllBytes();
        try (var underTest = new MergingFileInputStream(allFiles)) {
            //when
            final var actualBytes = underTest.readAllBytes();

            //then
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        }
    }

    @Test
    void testReadAllBytesShouldReadAllBytesAcrossTheChunksWhenCalledOnMatchingFiles() throws IOException {
        //given
        final var expectedBytes = getInputStream().readAllBytes();
        try (var underTest = new MergingFileInputStream(getResourcesFolder(), "a-", ".txt")) {
            //when
            final var actualBytes = underTest.readAllBytes();

            //then
            Assertions.assertArrayEquals(expectedBytes, actualBytes);
        }
    }

    private InputStream getInputStream() {
        return Objects.requireNonNull(getClass()
                .getResourceAsStream("/chunks/a.txt"));
    }

    private static Path getResourcesFolder() {
        return Optional.ofNullable(MergingFileInputStreamTest.class.getResource("/chunks/a.txt"))
                .map(URL::getFile)
                .map(File::new)
                .map(File::getParentFile)
                .map(File::toPath)
                .orElse(null);
    }

    private List<Path> fileList() {
        return Stream.of("a-1.txt", "a-2.txt", "a-3.txt", "a-4.txt")
                .map(s -> "/chunks/" + s)
                .map(getClass()::getResource)
                .filter(Objects::nonNull)
                .map(URL::getFile)
                .map(File::new)
                .map(File::toPath)
                .map(Path::toAbsolutePath)
                .toList();
    }
}
