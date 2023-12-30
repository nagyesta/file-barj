package com.github.nagyesta.filebarj.io.stream.internal.model;

import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

class BarjCargoEntityIndexTest {

    private static final String ENTRY_1 = "00000001";
    private static final String ENTRY_2 = "00000002";

    @Test
    void testGetContentOrElseMetadataShouldReturnContentWhenCalledWithRegularFile() {
        //given
        final var underTest = getFileEntity();

        //when
        final var actual = underTest.getContentOrElseMetadata();

        //then
        final var expected = underTest.getContent();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testGetContentOrElseMetadataShouldReturnMetadataWhenCalledWithDirectory() {
        //given
        final var underTest = getDirectoryEntity();

        //when
        final var actual = underTest.getContentOrElseMetadata();

        //then
        final var expected = underTest.getMetadata();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testToPropertiesShouldGeneratePropertiesEntryWhenCalledWithRegularFile() {
        //given
        final var underTest = getFileEntity();

        //when
        final var actual = underTest.toProperties(ENTRY_1);

        //then
        final var expected = getResourceAsString("/index/file.properties");
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testToPropertiesShouldGeneratePropertiesEntryWhenCalledWithDirectory() {
        //given
        final var underTest = getDirectoryEntity();

        //when
        final var actual = underTest.toProperties(ENTRY_1);

        //then
        final var expected = getResourceAsString("/index/directory.properties");
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testToPropertiesShouldGeneratePropertiesEntriesWithoutEmptyLinesWhenCalledWithFileThenDirectory() {
        //given
        final var file = getFileEntity();
        final var dir = getDirectoryEntity();

        //when
        final var actual = file.toProperties(ENTRY_1) + dir.toProperties(ENTRY_2);

        //then
        final var expected = getResourceAsString("/index/file-and-directory.properties");
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testFromPropertiesShouldParsePropertiesEntryWhenCalledWithRegularFile() throws IOException {
        //given
        final var expected = getFileEntity();
        final var properties = getProperties("/index/file.properties");

        //when
        final var actual = BarjCargoEntityIndex.fromProperties(properties, ENTRY_1);

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testFromPropertiesShouldParsePropertiesEntryWhenCalledWithDirectory() throws IOException {
        //given
        final var expected = getDirectoryEntity();
        final var properties = getProperties("/index/directory.properties");

        //when
        final var actual = BarjCargoEntityIndex.fromProperties(properties, ENTRY_1);

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testFromPropertiesShouldParsePropertiesEntryWhenCalledWithFileThenDirectory() throws IOException {
        //given
        final var expectedFile = getFileEntity();
        final var expectedDir = getDirectoryEntity();
        final var properties = getProperties("/index/file-and-directory.properties");

        //when
        final var actualFile = BarjCargoEntityIndex.fromProperties(properties, ENTRY_1);
        final var actualDir = BarjCargoEntityIndex.fromProperties(properties, ENTRY_2);

        //then
        Assertions.assertEquals(expectedDir, actualDir);
        Assertions.assertEquals(expectedFile, actualFile);
    }

    private String getResourceAsString(final String resource) {
        final var input = Objects.requireNonNull(getClass().getResourceAsStream(resource));
        return String.join("\n", IOUtils.readLines(input, StandardCharsets.UTF_8));
    }

    private Properties getProperties(final String resource) throws IOException {
        final var input = Objects.requireNonNull(getClass().getResourceAsStream(resource));
        final var properties = new Properties();
        properties.load(input);
        return properties;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static BarjCargoEntityIndex getFileEntity() {
        final var path = "path/to/entity.json";
        final var content = BarjCargoEntryBoundaries.builder()
                .absoluteStartIndexInclusive(1152)
                .absoluteEndIndexExclusive(2048)
                .chunkRelativeStartIndexInclusive(128)
                .startChunkName("chunk.0002.cargo")
                .chunkRelativeEndIndexExclusive(512)
                .endChunkName("chunk.0004.cargo")
                //sha 256 digest
                .originalHash("12345678901234567890123456789012")
                .originalSizeBytes(1024)
                .archivedHash("21098765432109876543210987654321")
                .archivedSizeBytes(996)
                .build();
        final var metadata = BarjCargoEntryBoundaries.builder()
                .absoluteStartIndexInclusive(2048)
                .absoluteEndIndexExclusive(2048)
                .chunkRelativeStartIndexInclusive(512)
                .startChunkName("chunk.0004.cargo")
                .chunkRelativeEndIndexExclusive(512)
                .endChunkName("chunk.0004.cargo")
                //sha 256 digest
                .originalHash("12345678901234567890123456789012")
                .originalSizeBytes(0)
                .archivedHash("21098765432109876543210987654321")
                .archivedSizeBytes(0)
                .build();
        return BarjCargoEntityIndex.builder()
                .path(path)
                .fileType(FileType.REGULAR_FILE)
                .content(content)
                .metadata(metadata)
                .build();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static BarjCargoEntityIndex getDirectoryEntity() {
        final var path = "path/to";
        final var metadata = BarjCargoEntryBoundaries.builder()
                .absoluteStartIndexInclusive(2048)
                .absoluteEndIndexExclusive(2176)
                .chunkRelativeStartIndexInclusive(512)
                .startChunkName("chunk.0004.cargo")
                .chunkRelativeEndIndexExclusive(128)
                .endChunkName("chunk.0005.cargo")
                //sha 256 digest
                .originalHash("12345678901234567890123456789012")
                .originalSizeBytes(100)
                .archivedHash("21098765432109876543210987654321")
                .archivedSizeBytes(128)
                .build();
        return BarjCargoEntityIndex.builder()
                .path(path)
                .fileType(FileType.DIRECTORY)
                .content(null)
                .metadata(metadata)
                .build();
    }
}
