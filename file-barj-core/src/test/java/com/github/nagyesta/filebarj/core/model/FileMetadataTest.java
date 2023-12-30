package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class FileMetadataTest {
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final var expected = FileMetadata.builder()
                .id(UUID.randomUUID())
                .fileSystemKey("fs-key")
                .absolutePath(Path.of("test", "file", ".path.txt").toAbsolutePath())
                .archiveMetadataId(UUID.randomUUID())
                .fileType(FileType.REGULAR_FILE)
                .owner("owner")
                .group("group")
                .posixPermissions("rwxr-xr-x")
                .originalSizeBytes(1024L)
                .lastModifiedUtcEpochSeconds(123L)
                .lastAccessedUtcEpochSeconds(234L)
                .createdUtcEpochSeconds(345L)
                .originalHash("hash")
                .hidden(true)
                .status(Change.NEW)
                .error("error")
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final FileMetadata actual = objectMapper.readerFor(FileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final var expected = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "file", "missing.md").toAbsolutePath())
                .fileType(FileType.SYMBOLIC_LINK)
                .status(Change.DELETED)
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final FileMetadata actual = objectMapper.readerFor(FileMetadata.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testAssertContentSourceShouldThrowExceptionWhenCalledOnDirectory() {
        //given
        final var underTest = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(Path.of("test", "dir").toAbsolutePath())
                .fileType(FileType.DIRECTORY)
                .status(Change.NEW)
                .build();

        //when
        Assertions.assertThrows(UnsupportedOperationException.class, underTest::assertContentSource);

        //then + exception
    }

    @Test
    void testStreamContentSourceShouldOpenStreamWhenCalledOnFile() throws IOException {
        final var tempFile = File.createTempFile("file", ".txt", new File(TEMP_DIR));
        tempFile.deleteOnExit();
        final var testValue = "test value";
        Files.writeString(tempFile.toPath(), testValue);
        //given
        final var underTest = FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(tempFile.toPath().toAbsolutePath())
                .fileType(FileType.REGULAR_FILE)
                .status(Change.NEW)
                .build();

        //when
        try (var stream = underTest.streamContent()) {
            final var actual = stream.readAllBytes();

            //then
            Assertions.assertArrayEquals(testValue.getBytes(StandardCharsets.UTF_8), actual);
        } finally {
            Assertions.assertTrue(tempFile.delete());
        }
    }
}
