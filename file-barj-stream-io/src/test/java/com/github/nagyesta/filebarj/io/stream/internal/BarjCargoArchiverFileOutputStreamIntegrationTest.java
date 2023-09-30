package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.TempFileAwareTest;
import com.github.nagyesta.filebarj.io.stream.*;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.io.stream.enums.EntityArchivalStage;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.mockito.Mockito.mock;

class BarjCargoArchiverFileOutputStreamIntegrationTest extends TempFileAwareTest {

    private static final String PIPE = "|";
    private static final String LINE_BREAK = "\n";

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
                .add(Arguments.of(Map.of("/key", "target"), true))
                .add(Arguments.of(Map.of("/key", "content"), false))
                .add(Arguments.of(Map.of("/key1", "link", "/key2", "target"), true))
                .add(Arguments.of(Map.of("/key1", ".hidden-link", "/key2", "ls", "/key3", "tmp"), false))
                .build();
    }

    @ParameterizedTest
    @MethodSource("randomFileContentProvider")
    void testAddFileEntityShouldSuccessfullyStoreFileEntriesWhenTheyAreWrittenToTheStream(
            final Map<String, Integer> entries, final int chunkSize, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = new TreeMap<String, String>();
        final var expectedContent = generateTestData(entries, contentMap, addMetadata);
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .maxFileSizeMebibyte(chunkSize)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var inputStream = new ByteArrayInputStream(value.getBytes());
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    underTest.addFileEntity(key, inputStream, null, metadata);
                } else {
                    underTest.addFileEntity(key, inputStream, null);
                }
            }));
            final var path = underTest.getCurrentFilePath();
            underTest.close();

            //then
            final var actualContent = Files.readString(path, StandardCharsets.UTF_8);
            Assertions.assertEquals(expectedContent, actualContent);
        }
    }

    @ParameterizedTest
    @MethodSource("randomLinkContentProvider")
    void testAddSymbolicLinkEntityShouldSuccessfullyStoreLinkEntriesWhenTheyAreWrittenToTheStream(
            final Map<String, String> entries, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = new TreeMap<>(entries);
        final var expectedContent = contentMap.entrySet().stream()
                .map(e -> {
                    final var builder = new StringBuilder(e.getValue());
                    if (addMetadata) {
                        builder.append(LINE_BREAK)
                                .append(e.getKey())
                                .append(PIPE)
                                .append(e.getValue().length())
                                .append(LINE_BREAK);
                    }
                    return builder.toString();
                }).collect(Collectors.joining());
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var metadata = LINE_BREAK + key + PIPE + value.length() + LINE_BREAK;
                if (addMetadata) {
                    underTest.addSymbolicLinkEntity(key, value, null, metadata);
                } else {
                    underTest.addSymbolicLinkEntity(key, value, null);
                }
            }));
            final var path = underTest.getCurrentFilePath();
            underTest.close();

            //then
            final var actualContent = Files.readString(path, StandardCharsets.UTF_8);
            Assertions.assertEquals(expectedContent, actualContent);
        }
    }

    @ParameterizedTest
    @MethodSource("randomLinkContentProvider")
    void testAddDirectoryEntityShouldSuccessfullyStoreDirectoryEntriesWhenTheyAreWrittenToTheStream(
            final Map<String, String> entries, final boolean addMetadata)
            throws IOException {
        //given
        final var contentMap = new TreeMap<>(entries);
        final var expectedContent = contentMap.values().stream()
                .map(e -> {
                    final var builder = new StringBuilder();
                    if (addMetadata) {
                        builder.append(e.length())
                                .append(LINE_BREAK);
                    }
                    return builder.toString();
                }).collect(Collectors.joining());
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                final var metadata = value.length() + LINE_BREAK;
                if (addMetadata) {
                    underTest.addDirectoryEntity(key, null, metadata);
                } else {
                    underTest.addDirectoryEntity(key, null);
                }
            }));
            final var path = underTest.getCurrentFilePath();
            underTest.close();

            //then
            final var actualContent = Files.readString(path, StandardCharsets.UTF_8);
            Assertions.assertEquals(expectedContent, actualContent);
        }
    }

    @Test
    void testAddingEntriesToExistingDirectoriesShouldSuccessfullyStoreEntriesWhenTheyAreWrittenToTheStream() throws IOException {
        //given
        final var entries = Map
                .of("/dir/file1", "content1", "/dir/file2", "content2", "/dir/sub/file3", "content3");
        final var contentMap = new TreeMap<>(entries);
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            underTest.addDirectoryEntity("/dir", null);
            underTest.addDirectoryEntity("/dir/sub", null);

            //when
            contentMap.forEach((key, value) -> Assertions.assertDoesNotThrow(() -> {
                underTest.addSymbolicLinkEntity(key, value, null);
            }));
            final var path = underTest.getCurrentFilePath();
            underTest.close();

            //then
            final var actualContent = Files.readString(path, StandardCharsets.UTF_8);
            Assertions.assertEquals(String.join("", contentMap.values()), actualContent);
        }
    }

    @Test
    void testAddingEntriesToUnknownDirectoriesShouldThrowExceptionWhenTheyAreWrittenToTheStream() throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest
                    .addSymbolicLinkEntity("dir/file", "value", null));

            //then + exception
        }
    }

    @Test
    void testAddingEntriesShouldThrowExceptionWhenTheyAreAttemptingToSlipIntoParentDirectories() throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest
                    .addSymbolicLinkEntity("../../file", "value", null));

            //then + exception
        }
    }

    @Test
    void testAddingEntriesShouldThrowExceptionWhenTheyAreAttemptingToAddAChildToANonDirectoryEntry() throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            underTest.addSymbolicLinkEntity("/file", "value", null);
            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest
                    .addSymbolicLinkEntity("/file/child", "value", null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testAddFileEntityShouldThrowExceptionWhenCalledWithNullName()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            final var inputStream = new ByteArrayInputStream("/test".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.addFileEntity(null, inputStream, null, "metadata"));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testAddFileEntityShouldThrowExceptionWhenCalledWithNullInputStream()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.addFileEntity("/key", null, null, "metadata"));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testAddSymbolicLinkEntityShouldThrowExceptionWhenCalledWithNullLinkPath()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.addSymbolicLinkEntity("/key", null, null, "metadata"));

            //then + exception
        }
    }

    @Test
    void testBarjCargoArchiverShouldThrowExceptionWhenTheEntryIsAlreadyOpen()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            Assertions.assertThrows(IllegalStateException.class,
                    () -> underTest.openEntity("/key2", FileType.REGULAR_FILE, null));

            //then + exception
        }
    }

    @Test
    void testMergeEntityShouldThrowExceptionWhenTheEntryIsAlreadyOpen()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            Assertions.assertThrows(IllegalStateException.class,
                    () -> underTest.mergeEntity(
                            mock(BarjCargoBoundarySource.class),
                            InputStream.nullInputStream(),
                            InputStream.nullInputStream()));

            //then + exception
        }
    }

    @Test
    void testMergeEntityShouldCopyFileContentAndMetadataWhenTheCalledWithValidFileEntity()
            throws IOException {
        //given
        final var sourceConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-source")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var targetConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var readConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPInputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        final var key = "/key";
        final var content = "test";
        final var metadata = "metadata";
        final var secretKey = EncryptionUtil.generateAesKey();
        try (var stream = new BarjCargoArchiverFileOutputStream(sourceConfig)) {
            final var contentStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            final var entity = stream.addFileEntity(key, contentStream, secretKey, metadata);
            stream.close();
            try (var contentFile = new FileInputStream(stream.getDataFilesWritten().get(0).toString());
                 var contentArchived = new FixedRangeInputStream(contentFile,
                         entity.getContentBoundary().getAbsoluteStartIndexInclusive(),
                         entity.getContentBoundary().getArchivedSizeBytes());
                 var metadataFile = new FileInputStream(stream.getDataFilesWritten().get(0).toString());
                 var metadataArchived = new FixedRangeInputStream(metadataFile,
                         entity.getMetadataBoundary().getAbsoluteStartIndexInclusive(),
                         entity.getMetadataBoundary().getArchivedSizeBytes());
                 var underTest = new BarjCargoArchiverFileOutputStream(targetConfig)) {
                //when
                underTest.mergeEntity(entity, contentArchived, metadataArchived);

                //then
                underTest.close();
                final var archiveEntry = new BarjCargoArchiveFileInputStreamSource(readConfig).getEntry(key);
                final var actualBytes = archiveEntry.getFileContent(secretKey).readAllBytes();
                final var actual = new String(actualBytes, StandardCharsets.UTF_8);
                Assertions.assertEquals(content, actual);
                final var actualMetadata = archiveEntry.getMetadata(secretKey);
                Assertions.assertEquals(metadata, actualMetadata);
            }
        }
    }

    @Test
    void testMergeEntityShouldCopyDirectoryMetadataWhenTheCalledWithValidDirectoryEntity()
            throws IOException {
        //given
        final var sourceConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-source")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var targetConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var readConfig = BarjCargoInputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPInputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexDecryptionKey(null)
                .build();
        final var key = "/key";
        final var metadata = "metadata";
        final var secretKey = EncryptionUtil.generateAesKey();
        try (var stream = new BarjCargoArchiverFileOutputStream(sourceConfig)) {
            final var entity = stream.addDirectoryEntity(key, secretKey, metadata);
            stream.close();
            try (var metadataFile = new FileInputStream(stream.getDataFilesWritten().get(0).toString());
                 var metadataArchived = new FixedRangeInputStream(metadataFile,
                         entity.getMetadataBoundary().getAbsoluteStartIndexInclusive(),
                         entity.getMetadataBoundary().getArchivedSizeBytes());
                 var underTest = new BarjCargoArchiverFileOutputStream(targetConfig)) {
                //when
                underTest.mergeEntity(entity, null, metadataArchived);

                //then
                underTest.close();
                final var archiveEntry = new BarjCargoArchiveFileInputStreamSource(readConfig).getEntry(key);
                final var actualMetadata = archiveEntry.getMetadata(secretKey);
                Assertions.assertEquals(metadata, actualMetadata);
            }
        }
    }

    @Test
    void testMergeEntityShouldThrowExceptionWhenTheCalledWithLinkEntityAndNullContentStream()
            throws IOException {
        //given
        final var sourceConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-source")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var targetConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var key = "/key";
        final var target = "target";
        final var metadata = "metadata";
        final var secretKey = EncryptionUtil.generateAesKey();
        try (var stream = new BarjCargoArchiverFileOutputStream(sourceConfig)) {
            final var entity = stream.addSymbolicLinkEntity(key, target, secretKey, metadata);
            stream.close();
            try (var metadataFile = new FileInputStream(stream.getDataFilesWritten().get(0).toString());
                 var metadataArchived = new FixedRangeInputStream(metadataFile,
                         entity.getMetadataBoundary().getAbsoluteStartIndexInclusive(),
                         entity.getMetadataBoundary().getArchivedSizeBytes());
                 var underTest = new BarjCargoArchiverFileOutputStream(targetConfig)) {
                //when
                Assertions.assertThrows(IllegalArgumentException.class,
                        () -> underTest.mergeEntity(entity, null, metadataArchived));

                //then + exception
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMergeEntityShouldThrowExceptionWhenTheCalledWithNullMetadataStream()
            throws IOException {
        //given
        final var sourceConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-source")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var targetConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var key = "/key";
        final var target = "target";
        final var metadata = "metadata";
        final var secretKey = EncryptionUtil.generateAesKey();
        try (var stream = new BarjCargoArchiverFileOutputStream(sourceConfig)) {
            final var entity = stream.addSymbolicLinkEntity(key, target, secretKey, metadata);
            stream.close();
            try (var contentFile = new FileInputStream(stream.getDataFilesWritten().get(0).toString());
                 var contentArchived = new FixedRangeInputStream(contentFile,
                         entity.getContentBoundary().getAbsoluteStartIndexInclusive(),
                         entity.getContentBoundary().getArchivedSizeBytes());
                 var underTest = new BarjCargoArchiverFileOutputStream(targetConfig)) {
                //when
                Assertions.assertThrows(IllegalArgumentException.class,
                        () -> underTest.mergeEntity(entity, contentArchived, null));

                //then + exception
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMergeEntityShouldThrowExceptionWhenTheCalledWithNullEntity()
            throws IOException {
        //given
        final var targetConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-target")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(targetConfig)) {
            //when
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> underTest.mergeEntity(null,
                            InputStream.nullInputStream(), InputStream.nullInputStream()));

            //then + exception
        }
    }

    @Test
    void testBarjCargoArchiverEntityConstructorShouldThrowExceptionWhenTheEntryIsAlreadyOpen()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            Assertions.assertThrows(IllegalStateException.class,
                    () -> new BarjCargoEntityArchiver("/key2", FileType.REGULAR_FILE, underTest, null));

            //then + exception
        }
    }

    @Test
    void testBarjCargoArchiverShouldThrowExceptionWhenTheEntryLifecycleIsStartedWithMetadataForRegularFile()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            final var entity = underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            Assertions.assertThrows(IllegalStateException.class, entity::openMetadataStream);

            //then + exception
        }
    }

    @Test
    void testBarjCargoArchiverShouldThrowExceptionWhenTheEntryLifecycleIsStartedWithContentForDirectory()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            final var entity = underTest.openEntity("/key1", FileType.DIRECTORY, null);
            Assertions.assertThrows(IllegalStateException.class, entity::openContentStream);

            //then + exception
        }
    }

    @Test
    void testBarjCargoArchiverShouldThrowExceptionWhenTheEntryContentStreamIsClosedBeforeOpen()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            final var entity = underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            Assertions.assertThrows(IllegalStateException.class, entity::closeContentStream);

            //then + exception
        }
    }

    @Test
    void testBarjCargoArchiverShouldAutoCompleteRemainingLifecycleWhenCloseIsCalledWhileContentIsOpen()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            final var entity = underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            entity.openContentStream();
            Assertions.assertEquals(EntityArchivalStage.CONTENT, entity.getStatus());

            //when
            entity.close();

            //then
            Assertions.assertEquals(EntityArchivalStage.CLOSED, entity.getStatus());
        }
    }

    @Test
    void testBarjCargoArchiverShouldAutoCompleteRemainingLifecycleWhenCloseIsCalledWhileMetadataIsOpen()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            final var entity = underTest.openEntity("/key1", FileType.DIRECTORY, null);
            entity.openMetadataStream();
            Assertions.assertEquals(EntityArchivalStage.METADATA, entity.getStatus());

            //when
            entity.close();

            //then
            Assertions.assertEquals(EntityArchivalStage.CLOSED, entity.getStatus());
        }
    }

    @Test
    void testBarjCargoArchiverShouldThrowExceptionWhenTheEntryNameIsAlreadyUsed()
            throws IOException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test")
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        try (var underTest = new BarjCargoArchiverFileOutputStream(config)) {
            //when
            underTest.openEntity("/key1", FileType.REGULAR_FILE, null);
            underTest.closeCurrentEntity();
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.openEntity("/key1", FileType.REGULAR_FILE, null));

            //then + exception
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testWritingExampleFileShouldProduceExpectedOutput() throws IOException, URISyntaxException {
        //given
        final var config = BarjCargoOutputStreamConfiguration.builder()
                .hashAlgorithm("SHA-256")
                .prefix("barj")
                .maxFileSizeMebibyte(1)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .folder(getTestDataRoot())
                .build();

        //when
        try (var stream = new BarjCargoArchiverFileOutputStream(config)) {
            stream.addDirectoryEntity("/dir", null,
                    "arbitrary metadata of /dir");
            stream.addFileEntity("/dir/file1.ext", new ByteArrayInputStream("file1 content".getBytes()), null,
                    "arbitrary metadata of /dir/file1.ext");
            stream.addSymbolicLinkEntity("/dir/file2.ext", "/dir/file1.txt", null,
                    "arbitrary metadata of /dir/file2.ext");
            stream.addFileEntity("/dir/file3.ext", new ByteArrayInputStream("file3 content".getBytes()), null,
                    "arbitrary metadata of /dir/file3.ext");
            stream.close();

            //then
            final var actualIndexLines = Files.readAllLines(stream.getIndexFile());
            final var expectedIndexLines = Files.readAllLines(
                    Path.of(getClass().getResource("/example/index.properties").toURI()));
            Assertions.assertIterableEquals(expectedIndexLines, actualIndexLines);
            final var actualCargoLines = Files.readAllLines(stream.getDataFilesWritten().get(0));
            final var expectedCargoLines = Files.readAllLines(
                    Path.of(getClass().getResource("/example/content.cargo").toURI()));
            Assertions.assertIterableEquals(expectedCargoLines, actualCargoLines);
        }

    }

    private String generateTestData(
            final Map<String, Integer> input, final TreeMap<String, String> target, final boolean metadata) {
        final var expectedContentBuilder = new StringBuilder();
        new TreeMap<>(input).forEach((key, value) -> {
            final var randomString = randomString(value);
            expectedContentBuilder.append(randomString);
            if (metadata) {
                expectedContentBuilder.append(LINE_BREAK)
                        .append(key)
                        .append(PIPE).append(value)
                        .append(LINE_BREAK);
            }
            target.put(key, randomString);
        });
        return expectedContentBuilder.toString();
    }

    private String randomString(final int length) {
        final var random = new Random();
        final var builder = new StringBuilder();
        for (var i = 0; i < length; i++) {
            builder.append((char) ('a' + random.nextInt('z' - 'a')));
        }
        return builder.toString();
    }
}
