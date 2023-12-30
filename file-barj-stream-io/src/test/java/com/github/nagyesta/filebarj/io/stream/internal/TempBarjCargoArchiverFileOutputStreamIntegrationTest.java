package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.TempFileAwareTest;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiveFileInputStreamSource;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoInputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

class TempBarjCargoArchiverFileOutputStreamIntegrationTest extends TempFileAwareTest {

    @Test
    void testMergeEntityShouldCopyFileContentAndMetadataWhenTheCalledWithValidFileEntityOfATempArchive()
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
        try (var stream = new TempBarjCargoArchiverFileOutputStream(sourceConfig, key)) {
            final var contentStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            final var entity = stream.addFileEntity(key, contentStream, secretKey, metadata);
            stream.close();
            try (var underTest = new BarjCargoArchiverFileOutputStream(targetConfig)) {
                //when
                underTest.mergeEntity(entity,
                        stream.getStream(entity.getContentBoundary()),
                        stream.getStream(entity.getMetadataBoundary()));

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
    void testDeleteTempArchiverShouldDeleteTempFileWhenCalled()
            throws IOException {
        //given
        final var sourceConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-source")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var key = "/key";
        final var content = "test";
        final var metadata = "metadata";
        final var secretKey = EncryptionUtil.generateAesKey();
        try (var underTest = new TempBarjCargoArchiverFileOutputStream(sourceConfig, key)) {
            final var contentStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            underTest.addFileEntity(key, contentStream, secretKey, metadata);
            underTest.close();
            underTest.delete();
            Assertions.assertTrue(underTest.getDataFilesWritten().stream().noneMatch(Files::exists));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetStreamOfTempArchiverShouldThrowExceptionWhenCalledWithNull()
            throws IOException {
        //given
        final var sourceConfig = BarjCargoOutputStreamConfiguration.builder()
                .prefix("integration-test-source")
                .compressionFunction(GZIPOutputStream::new)
                .hashAlgorithm("sha-256")
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build();
        final var key = "/key";
        final var content = "test";
        final var metadata = "metadata";
        final var secretKey = EncryptionUtil.generateAesKey();
        try (var underTest = new TempBarjCargoArchiverFileOutputStream(sourceConfig, key)) {
            final var contentStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            underTest.addFileEntity(key, contentStream, secretKey, metadata);
            underTest.close();

            //when
            Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.getStream(null));

            //then + exception
        }
    }
}
