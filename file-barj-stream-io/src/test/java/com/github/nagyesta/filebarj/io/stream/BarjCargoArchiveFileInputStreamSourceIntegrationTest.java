package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.TempFileAwareTest;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.exception.ArchiveIntegrityException;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream.MEBIBYTE;
import static org.mockito.Mockito.mock;

class BarjCargoArchiveFileInputStreamSourceIntegrationTest extends TempFileAwareTest {

    private static final int SIZE_50_MIB = (int) (50 * MEBIBYTE);
    private static final Random RANDOM = new Random();
    private static final byte[] RANDOM_DATA = random();

    private static final String SHA_256 = "SHA-256";
    private static final String PIPE = "|";
    private static final String LINE_BREAK = "\n";
    private static final String PREFIX = "unpack-integration-test";

    @SuppressWarnings({"checkstyle:MagicNumber", "MagicNumber"})
    public static Stream<Arguments> randomFileContentProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(Map.of("/key", 1), 1, true))
                .add(Arguments.of(Map.of("/key", 1024), 2, false))
                .add(Arguments.of(Map.of("/key1", 1024, "/key2", 1), 10, true))
                .add(Arguments.of(Map.of("/key1", 512, "/key2", 4096, "/key3", 20480), 100, false))
                .build();
    }

    public static Stream<Arguments> randomLinkContentProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(Map.of("/key", "/path/to/target"), true))
                .add(Arguments.of(Map.of("/key", "/link/content"), false))
                .add(Arguments.of(Map.of("/key1", "/home/user/link", "/key2", "/path/to/target"), true))
                .add(Arguments.of(Map.of("/key1", "/home/user/.hidden/link", "/key2", "/usr/bin/ls", "/key3", "/tmp"), false))
                .build();
    }

    @ParameterizedTest
    @MethodSource("randomLinkContentProvider")
    void testGetStreamForShouldReturnStreamWithOriginalLinkContentWhenCalledWithExistingPathUsingEncryption(
            final Map<String, String> entries, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_INPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            entries.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addSymbolicLinkEntity(key, value, secretKey, metadata);
                } else {
                    testDataWriter.addSymbolicLinkEntity(key, value, secretKey);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            final var entry = underTest.getEntry(fileName);
            final var actualPath = entry.getPath();
            final var actualContent = entry.getLinkTarget(secretKey);
            final var actualMetadata = entry.getMetadata(secretKey);

            //then
            Assertions.assertEquals(fileName, actualPath);
            final var expectedContent = entries.get(fileName);
            Assertions.assertEquals(expectedContent, actualContent);
            if (addMetadata) {
                final var expected = LINE_BREAK + fileName + PIPE + expectedContent.length() + LINE_BREAK;
                Assertions.assertEquals(expected, actualMetadata);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetStreamForShouldReturnStreamWithOriginalContentWhenCalledWithExistingPathUsingEncryption(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_INPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, secretKey, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, secretKey);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            final var entry = underTest.getEntry(fileName);
            final var actualPath = entry.getPath();
            final var actualContent = entry.getFileContent(secretKey).readAllBytes();
            final var actualMetadata = entry.getMetadata(secretKey);

            //then
            Assertions.assertEquals(fileName, actualPath);
            final var expectedContent = contentMap.get(fileName);
            final var actualContentString = new String(actualContent, StandardCharsets.UTF_8);
            Assertions.assertEquals(expectedContent, actualContentString);
            if (addMetadata) {
                final var expected = LINE_BREAK + fileName + PIPE + expectedContent.length() + LINE_BREAK;
                Assertions.assertEquals(expected, actualMetadata);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetStreamForShouldReturnStreamWithOriginalContentWhenCalledWithExistingPathUsingCompressionAndEncryption(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, secretKey, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, secretKey);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            final var actualContent = underTest.getEntry(fileName).getFileContent(secretKey).readAllBytes();

            //then
            final var expectedContent = contentMap.get(fileName);
            final var actualContentString = new String(actualContent, StandardCharsets.UTF_8);
            Assertions.assertEquals(expectedContent, actualContentString);
        }
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetNextStreamForShouldReturnStreamWithOriginalFileContentWhenCalledForTheIterator(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, secretKey, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, secretKey);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            var found = false;
            final var iterator = underTest.getIterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                final var key = entry.getPath();
                if (!key.equals(fileName)) {
                    entry.skipContent();
                    entry.skipMetadata();
                    continue;
                }
                found = true;
                final var actualContent = entry.getFileContent(secretKey).readAllBytes();
                final var expectedContent = contentMap.get(key);
                final var actualContentString = new String(actualContent, StandardCharsets.UTF_8);
                if (addMetadata) {
                    final var expectedMetadataContent = LINE_BREAK + key + PIPE + expectedContent.length() + LINE_BREAK;
                    final var actualMetadataContent = entry.getMetadata(secretKey);
                    //then
                    Assertions.assertEquals(expectedMetadataContent, actualMetadataContent);
                } else {
                    Assertions.assertNull(entry.getMetadata(secretKey));
                }
                Assertions.assertEquals(expectedContent, actualContentString);
            }
            Assertions.assertTrue(found);
        }
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetLinkTargetShouldThrowExceptionsWhenCalledForRegularFile(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, secretKey, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, secretKey);
                }
            }));
            testDataWriter.close();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            final var iterator = underTest.getIterator();
            final var entry = iterator.next();
            Assertions.assertThrows(IllegalArgumentException.class, () -> entry.getLinkTarget(secretKey));

            //then + exception
        }
    }

    @ParameterizedTest
    @MethodSource("randomLinkContentProvider")
    void testGetNextStreamForShouldReturnStreamWithOriginalLinkTargetsWhenCalledForTheIterator(
            final Map<String, String> entries, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            entries.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addSymbolicLinkEntity(key, value, secretKey, metadata);
                } else {
                    testDataWriter.addSymbolicLinkEntity(key, value, secretKey);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            var found = false;
            final var iterator = underTest.getIterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                final var key = entry.getPath();
                if (!key.equals(fileName)) {
                    entry.skipContent();
                    entry.skipMetadata();
                    continue;
                }
                found = true;
                final var actualContent = entry.getLinkTarget(secretKey);
                final var expectedContent = entries.get(key);
                if (addMetadata) {
                    final var expectedMetadataContent = LINE_BREAK + key + PIPE + expectedContent.length() + LINE_BREAK;
                    final var actualMetadataContent = entry.getMetadata(secretKey);
                    //then
                    Assertions.assertEquals(expectedMetadataContent, actualMetadataContent);
                } else {
                    Assertions.assertNull(entry.getMetadata(secretKey));
                }
                Assertions.assertEquals(expectedContent, actualContent);
            }
            Assertions.assertTrue(found);
        }
    }

    @ParameterizedTest
    @MethodSource("randomLinkContentProvider")
    void testGetFileContentShouldThrowExceptionsWhenCalledForSymbolicLink(
            final Map<String, String> entries, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            entries.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addSymbolicLinkEntity(key, value, secretKey, metadata);
                } else {
                    testDataWriter.addSymbolicLinkEntity(key, value, secretKey);
                }
            }));
            testDataWriter.close();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            final var iterator = underTest.getIterator();
            final var entry = iterator.next();
            Assertions.assertThrows(IllegalArgumentException.class, () -> entry.getFileContent(secretKey));

            //then + exception
        }
    }

    @ParameterizedTest
    @MethodSource("randomLinkContentProvider")
    void testGetNextStreamForShouldReturnStreamWithDirectoryMetadataWhenCalledForTheIterator(
            final Map<String, String> entries, final boolean addMetadata)
            throws IOException {
        //given
        final var secretKey = EncryptionUtil.generateAesKey();
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(secretKey)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(secretKey)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            entries.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addDirectoryEntity(key, secretKey, metadata);
                } else {
                    testDataWriter.addDirectoryEntity(key, secretKey);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            var found = false;
            final var iterator = underTest.getIterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                final var key = entry.getPath();
                if (!key.equals(fileName)) {
                    entry.skipContent();
                    entry.skipMetadata();
                    continue;
                }
                found = true;
                entry.skipContent();
                if (addMetadata) {
                    final var expectedMetadataContent = LINE_BREAK + key + PIPE + entries.get(key).length() + LINE_BREAK;
                    final var actualMetadataContent = entry.getMetadata(secretKey);
                    //then
                    Assertions.assertEquals(expectedMetadataContent, actualMetadataContent);
                } else {
                    Assertions.assertNull(entry.getMetadata(secretKey));
                }
            }
            Assertions.assertTrue(found);
        }
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetStreamForShouldReturnStreamWithOriginalContentWhenCalledWithExistingPathUsingCompression(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, null, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, null);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            final var actualContent = underTest.getEntry(fileName).getFileContent(null).readAllBytes();

            //then
            final var expectedContent = contentMap.get(fileName);
            final var actualContentString = new String(actualContent, StandardCharsets.UTF_8);
            Assertions.assertEquals(expectedContent, actualContentString);
        }
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetStreamForShouldCloseOriginalStreamWhenExceptionIsThrownDuringCreation(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final List<InputStream> streams = new ArrayList<>();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(in -> {
                    final var stream = new GzipCompressorInputStream(in);
                    streams.add(stream);
                    return stream;
                })
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, null, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, null);
                }
            }));
            testDataWriter.close();
            final var fileName = entries.keySet().iterator().next();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);
            final var invalidIndexBoundary = BarjCargoEntryBoundaries.builder()
                    .chunkRelativeStartIndexInclusive(-1)
                    .chunkRelativeEndIndexExclusive(-1)
                    .absoluteStartIndexInclusive(-1)
                    .absoluteEndIndexExclusive(-1)
                    .startChunkName(fileName)
                    .endChunkName(fileName)
                    .archivedHash(null)
                    .originalHash(null)
                    .archivedSizeBytes(-1)
                    .originalSizeBytes(-1)
                    .build();

            //when
            Assertions.assertThrows(IOException.class,
                    () -> underTest.getStreamFor(invalidIndexBoundary, null));

            //then + exception
            //the closed stream will return -1 when read
            Assertions.assertEquals(-1, streams.get(0).read());
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetNextStreamForShouldThrowExceptionWhenCalledWithNullInputStream()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.getNextStreamFor(null, mock(BarjCargoEntryBoundaries.class), null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetNextStreamForShouldThrowExceptionWhenCalledWithNullEntity()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.getNextStreamFor(InputStream.nullInputStream(), null, null));

        //then + exception
    }

    @Test
    void testGetNextStreamForShouldThrowExceptionWhenCalledWithInvalidEntity()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IOException.class,
                () -> underTest.getNextStreamFor(InputStream.nullInputStream(), mock(BarjCargoEntryBoundaries.class), null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testOpenStreamForSequentialAccessShouldThrowExceptionWhenCalledWithNullPathList()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.openStreamForSequentialAccess(null, List.of()));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testOpenStreamForSequentialAccessShouldThrowExceptionWhenCalledWithNullEntityList()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.openStreamForSequentialAccess(List.of(), null));

        //then + exception
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testIteratorShouldPresentEntitiesSequentiallyWhenCalledWithValidScope()
            throws IOException {
        //given
        final var contentMap = generateTestData(IntStream.range(0, 50)
                .boxed()
                .collect(Collectors.toMap(i -> "/key" + i, i -> 1024 * i)));
        final var underTest = getStreamSource(contentMap);
        final var scope = contentMap.keySet().stream()
                .filter(s -> s.matches("/key.[02468]"))
                .limit(10)
                .collect(Collectors.toCollection(TreeSet::new));

        //when
        final var entries = underTest.getMatchingEntriesInOrderOfOccurrence(scope);
        final var actualSet = entries.stream()
                .map(BarjCargoEntityIndex::getPath)
                .collect(Collectors.toCollection(TreeSet::new));
        try (var iterator = underTest.getIteratorForScope(actualSet)) {
            final var found = new ArrayList<>();
            while (iterator.hasNext()) {
                final var current = iterator.next();
                if (!actualSet.contains(current.getPath())) {
                    current.skipContent();
                    current.skipMetadata();
                } else {
                    found.add(current.getPath());
                    final var actualContent = new String(current
                            .getFileContent(null).readAllBytes(), StandardCharsets.UTF_8);
                    current.skipMetadata();

                    //then
                    Assertions.assertEquals(contentMap.get(current.getPath()), actualContent);
                }
            }
            Assertions.assertIterableEquals(scope, actualSet);
            Assertions.assertIterableEquals(actualSet, found);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetIteratorForScopeShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.getIteratorForScope(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetEntityShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.getEntry(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetMatchingEntriesInOrderOfOccurrenceShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var underTest = getStreamSource(generateTestData(Map.of("/key1", 1)));

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> underTest.getMatchingEntriesInOrderOfOccurrence(null));

        //then + exception
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetEntryShouldThrowExceptionWhenEntryIsNotFound(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, null, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, null);
                }
            }));
            testDataWriter.close();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.getEntry(UUID.randomUUID().toString()));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testGetStreamForShouldThrowExceptionWhenCalledWithNullBoundary(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = generateTestData(entries);
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    testDataWriter.addFileEntity(key, inputStream, null, metadata);
                } else {
                    testDataWriter.addFileEntity(key, inputStream, null);
                }
            }));
            testDataWriter.close();
            final var underTest = new BarjCargoArchiveFileInputStreamSource(inConfig);

            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.getStreamFor(null, null));

            //then + exception
        }
    }

    @Test
    void testVerifyHashesShouldReturnWithoutVerificationWhenHashAlgorithmIsNull() throws IOException {
        //given
        final var outConfig = getBarjCargoOutputStreamConfiguration(null, IoFunction.IDENTITY_OUTPUT_STREAM);
        try (var out = new BarjCargoArchiverFileOutputStream(outConfig)) {
            out.addFileEntity("/" + UUID.randomUUID().toString(), new ByteArrayInputStream(RANDOM_DATA), null);
        }

        final var inConfig = getBarjCargoInputStreamConfiguration(null, IoFunction.IDENTITY_INPUT_STREAM);
        final var in = new BarjCargoArchiveFileInputStreamSource(inConfig);

        //when
        Assertions.assertDoesNotThrow(in::verifyHashes);

        //then + exception
    }

    @Test
    void testVerifyHashesShouldVerifyHashesWhenHashAlgorithmIsSha256() throws IOException {
        //given
        final var outConfig = getBarjCargoOutputStreamConfiguration(SHA_256, GzipCompressorOutputStream::new);
        try (var out = new BarjCargoArchiverFileOutputStream(outConfig)) {
            out.addFileEntity("/" + UUID.randomUUID().toString(), new ByteArrayInputStream(RANDOM_DATA), null);
        }

        final var inConfig = getBarjCargoInputStreamConfiguration(SHA_256, GzipCompressorInputStream::new);
        final var in = new BarjCargoArchiveFileInputStreamSource(inConfig);

        //when
        Assertions.assertDoesNotThrow(in::verifyHashes);

        //then + no exception
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void testVerifyHashesShouldThrowExceptionWhenTheFileOrderIsChanged() throws IOException {
        //given
        final var outConfig = getBarjCargoOutputStreamConfiguration(SHA_256, GzipCompressorOutputStream::new);
        try (var out = new BarjCargoArchiverFileOutputStream(outConfig)) {
            out.addFileEntity("/" + UUID.randomUUID().toString(), new ByteArrayInputStream(RANDOM_DATA), null);
            out.close();
            final var written = out.getDataFilesWritten();
            final var firstPath = written.get(0);
            final var secondPath = written.get(1);
            final var temp = new File(firstPath.toAbsolutePath() + ".bak");
            firstPath.toFile().renameTo(temp);
            secondPath.toFile().renameTo(firstPath.toAbsolutePath().toFile());
            temp.renameTo(secondPath.toFile());
        }

        final var inConfig = getBarjCargoInputStreamConfiguration(SHA_256, GzipCompressorInputStream::new);
        final var in = new BarjCargoArchiveFileInputStreamSource(inConfig);

        //when
        Assertions.assertThrows(ArchiveIntegrityException.class, in::verifyHashes);

        //then + exception
    }

    @Test
    void testVerifyHashesShouldThrowExceptionWhenTheStreamDoesNotEndAfterTheLastEntry() throws IOException {
        //given
        final var outConfig = getBarjCargoOutputStreamConfiguration(SHA_256, GzipCompressorOutputStream::new);
        try (var out = new BarjCargoArchiverFileOutputStream(outConfig)) {
            out.addFileEntity("/" + UUID.randomUUID().toString(), new ByteArrayInputStream(RANDOM_DATA), null);
            out.write(1);
        }

        final var inConfig = getBarjCargoInputStreamConfiguration(SHA_256, GzipCompressorInputStream::new);
        final var in = new BarjCargoArchiveFileInputStreamSource(inConfig);

        //when
        Assertions.assertThrows(ArchiveIntegrityException.class, in::verifyHashes);

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenTheLastFileIsMissing() throws IOException {
        //given
        final var outConfig = getBarjCargoOutputStreamConfiguration(SHA_256, GzipCompressorOutputStream::new);
        try (var out = new BarjCargoArchiverFileOutputStream(outConfig)) {
            out.addFileEntity("/" + UUID.randomUUID().toString(), new ByteArrayInputStream(RANDOM_DATA), null);
            out.close();
            Assertions.assertTrue(out.getCurrentFilePath().toFile().delete());
        }

        final var inConfig = getBarjCargoInputStreamConfiguration(SHA_256, GzipCompressorInputStream::new);

        //when
        Assertions.assertThrows(ArchiveIntegrityException.class, () -> new BarjCargoArchiveFileInputStreamSource(inConfig));

        //then + exception
    }

    @Test
    void testConstructorShouldThrowExceptionWhenTheSizeOfTheLastFileDoesNotMatch() throws IOException {
        //given
        final var outConfig = getBarjCargoOutputStreamConfiguration(SHA_256, GzipCompressorOutputStream::new);
        try (var out = new BarjCargoArchiverFileOutputStream(outConfig)) {
            out.addFileEntity("/" + UUID.randomUUID().toString(), new ByteArrayInputStream(RANDOM_DATA), null);
            out.close();
            try (var hack = new FileOutputStream(out.getCurrentFilePath().toFile(), true)) {
                hack.write(1);
            }
        }

        final var inConfig = getBarjCargoInputStreamConfiguration(SHA_256, GzipCompressorInputStream::new);

        //when
        Assertions.assertThrows(ArchiveIntegrityException.class, () -> new BarjCargoArchiveFileInputStreamSource(inConfig));

        //then + exception
    }


    @Test
    void testReadingExampleFileShouldReadExpectedInput() throws IOException, URISyntaxException {
        //given
        final var writeConfig = BarjCargoOutputStreamConfiguration.builder()
                .hashAlgorithm("SHA-256")
                .prefix("barj")
                .maxFileSizeMebibyte(1)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .folder(getTestDataRoot())
                .build();

        final var dirMetadata = "arbitrary metadata of /dir";
        final var file1Metadata = "arbitrary metadata of /dir/file1.ext";
        final var file2Metadata = "arbitrary metadata of /dir/file2.ext";
        final var file3Metadata = "arbitrary metadata of /dir/file3.ext";
        final var file1Content = "file1 content".getBytes();
        final var file3Content = "file3 content".getBytes();
        final var file2LinkPath = "/dir/file1.txt";
        final var dirPath = "/dir";
        final var file1Path = "/dir/file1.ext";
        final var file2Path = "/dir/file2.ext";
        final var file3Path = "/dir/file3.ext";
        try (var stream = new BarjCargoArchiverFileOutputStream(writeConfig)) {
            stream.addDirectoryEntity(dirPath, null, dirMetadata);
            stream.addFileEntity(file1Path, new ByteArrayInputStream(file1Content), null, file1Metadata);
            stream.addSymbolicLinkEntity(file2Path, file2LinkPath, null, file2Metadata);
            stream.addFileEntity(file3Path, new ByteArrayInputStream(file3Content), null, file3Metadata);
        }

        final var config = BarjCargoInputStreamConfiguration.builder()
                .hashAlgorithm("SHA-256")
                .prefix("barj")
                .compressionFunction(IoFunction.IDENTITY_INPUT_STREAM)
                .folder(getTestDataRoot())
                .build();

        //when
        final var source = new BarjCargoArchiveFileInputStreamSource(config);
        final var iterator = source.getIterator();
        //then
        Assertions.assertTrue(iterator.hasNext());
        //verify /dir
        final var dir = iterator.next();
        Assertions.assertEquals(FileType.DIRECTORY, dir.getFileType());
        Assertions.assertEquals(dirPath, dir.getPath());
        Assertions.assertEquals(dirMetadata, dir.getMetadata(null));
        //verify /dir/file1.ext
        Assertions.assertTrue(iterator.hasNext());
        final var file1 = iterator.next();
        Assertions.assertEquals(FileType.REGULAR_FILE, file1.getFileType());
        Assertions.assertEquals(file1Path, file1.getPath());
        //the order is important, read content first!
        Assertions.assertArrayEquals(file1Content, file1.getFileContent(null).readAllBytes());
        Assertions.assertEquals(file1Metadata, file1.getMetadata(null));
        //verify /dir/file2.ext
        Assertions.assertTrue(iterator.hasNext());
        final var file2 = iterator.next();
        Assertions.assertEquals(FileType.SYMBOLIC_LINK, file2.getFileType());
        Assertions.assertEquals(file2Path, file2.getPath());
        Assertions.assertEquals(file2LinkPath, file2.getLinkTarget(null));
        Assertions.assertEquals(file2Metadata, file2.getMetadata(null));
        //verify /dir/file3.ext
        Assertions.assertTrue(iterator.hasNext());
        final var file3 = iterator.next();
        Assertions.assertEquals(FileType.REGULAR_FILE, file3.getFileType());
        Assertions.assertEquals(file3Path, file3.getPath());
        Assertions.assertArrayEquals(file3Content, file3.getFileContent(null).readAllBytes());
        Assertions.assertEquals(file3Metadata, file3.getMetadata(null));

        Assertions.assertFalse(iterator.hasNext());
    }

    private BarjCargoArchiveFileInputStreamSource getStreamSource(
            final Map<String, String> contentMap) throws IOException {
        final var outConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorOutputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var inConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(GzipCompressorInputStream::new)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        try (var testDataWriter = new BarjCargoArchiverFileOutputStream(outConfig)) {
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                testDataWriter.addFileEntity(key, inputStream, null);
            }));
        }
        return new BarjCargoArchiveFileInputStreamSource(inConfig);
    }

    private static byte[] random() {
        final var result = new byte[SIZE_50_MIB];
        RANDOM.nextBytes(result);
        return result;
    }

    private BarjCargoOutputStreamConfiguration getBarjCargoOutputStreamConfiguration(
            final String hashAlgorithm, final IoFunction<OutputStream, OutputStream> compressionFunction) {
        return BarjCargoOutputStreamConfiguration.builder()
                .prefix("prefix")
                .hashAlgorithm(hashAlgorithm)
                .folder(getTestDataRoot())
                .compressionFunction(compressionFunction)
                .maxFileSizeMebibyte(1)
                .build();
    }

    private BarjCargoInputStreamConfiguration getBarjCargoInputStreamConfiguration(
            final String hashAlgorithm, final IoFunction<InputStream, InputStream> compressionFunction) {
        return BarjCargoInputStreamConfiguration.builder()
                .prefix("prefix")
                .hashAlgorithm(hashAlgorithm)
                .folder(getTestDataRoot())
                .compressionFunction(compressionFunction)
                .build();
    }

    private SortedMap<String, String> generateTestData(
            final Map<String, Integer> input) {
        final SortedMap<String, String> target = new TreeMap<>();
        new TreeMap<>(input).forEach((key, value) -> {
            final var randomString = randomString(value);
            target.put(key, randomString);
        });
        return target;
    }

    private String randomString(final int length) {
        final var builder = new StringBuilder();
        for (var i = 0; i < length; i++) {
            builder.append((char) ('a' + RANDOM.nextInt('z' - 'a')));
        }
        return builder.toString();
    }
}
