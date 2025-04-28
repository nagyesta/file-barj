package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.TempFileAwareTest;
import com.github.nagyesta.filebarj.io.stream.BarjCargoArchiverFileOutputStream;
import com.github.nagyesta.filebarj.io.stream.BarjCargoOutputStreamConfiguration;
import com.github.nagyesta.filebarj.io.stream.IoFunction;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

class ArchiveEntryOutputStreamTest extends TempFileAwareTest {

    private static final SecretKey SECRET_KEY = EncryptionUtil.generateAesKey();
    private static final byte[] IV = EncryptionUtil.generateSecureRandomBytesForGcmIv();
    private static final byte[] CONTENT = "content".getBytes();
    private static final byte[] ZIP_CONTENT = zipContent(CONTENT);
    private static final byte[] ENCRYPTED_CONTENT = encryptContent(CONTENT, SECRET_KEY, IV);
    private static final byte[] ENCRYPTED_ZIP_CONTENT = encryptContent(ZIP_CONTENT, SECRET_KEY, IV);
    private static final String PLAIN_HASH = DigestUtils.sha256Hex(CONTENT);
    private static final String ZIP_HASH = DigestUtils.sha256Hex(ZIP_CONTENT);
    private static final byte[] IV_AND_ENCRYPTED_CONTENT = join(IV, ENCRYPTED_CONTENT);
    private static final String ENCRYPTED_HASH = DigestUtils.sha256Hex(IV_AND_ENCRYPTED_CONTENT);
    private static final byte[] IV_AND_ENCRYPTED_ZIP_CONTENT = join(IV, ENCRYPTED_ZIP_CONTENT);
    private static final String ENCRYPTED_ZIP_HASH = DigestUtils.sha256Hex(IV_AND_ENCRYPTED_ZIP_CONTENT);
    private static final String PREFIX_00001_CARGO = "prefix.00001.cargo";
    private static final String PREFIX_00010_CARGO = "prefix.00010.cargo";
    private static final String SHA_256 = "sha-256";
    private static final String PREFIX = "prefix";
    private static final int MEBIBYTE = 1024 * 1024;
    private static final int TEN_MEBIBYTE_MINUS_100 = 10 * MEBIBYTE - 100;

    @Test
    void testConstructorShouldCloseIntermediateStreamsWhenAnExceptionIsThrownDuringTheirCreation() throws IOException {
        //given
        final var cipherStream = mock(CipherOutputStream.class);
        final var compressionStream = mock(OutputStream.class);
        try (var cargoStream = new BarjCargoArchiverFileOutputStream(BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(out -> compressionStream)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build())) {
            final var parent = spy(cargoStream);
            final var counter = new AtomicInteger(1);
            doAnswer(a -> {
                if (counter.getAndIncrement() == 2) {
                    throw new IllegalStateException("Test exception");
                }
                return SHA_256;
            }).when(parent).getHashAlgorithm();

            //when
            Assertions.assertThrows(IllegalStateException.class,
                    () -> new ArchiveEntryOutputStream(parent, out -> cipherStream));

            //then
            verify(cipherStream).close();
            verify(compressionStream).close();
            verify(parent, times(2)).getHashAlgorithm();
        }
    }

    @Test
    void testGetEntityBoundaryShouldReturnBoundariesWhenCalledAfterClose() throws IOException {
        //given
        try (var cargoStream = new BarjCargoArchiverFileOutputStream(BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build())) {
            final var expected = BarjCargoEntryBoundaries.builder()
                    .startChunkName(PREFIX_00001_CARGO)
                    .endChunkName(PREFIX_00001_CARGO)
                    .archivedHash(PLAIN_HASH)
                    .archivedSizeBytes(CONTENT.length)
                    .originalHash(PLAIN_HASH)
                    .originalSizeBytes(CONTENT.length)
                    .chunkRelativeStartIndexInclusive(0)
                    .chunkRelativeEndIndexExclusive(CONTENT.length)
                    .absoluteStartIndexInclusive(0)
                    .absoluteEndIndexExclusive(CONTENT.length)
                    .build();
            final var underTest = new ArchiveEntryOutputStream(cargoStream, IoFunction.IDENTITY_OUTPUT_STREAM);

            //when
            underTest.write(CONTENT);
            underTest.close();
            final var actual = underTest.getEntityBoundary();

            //then
            Assertions.assertEquals(expected, actual);
            Assertions.assertArrayEquals(CONTENT, Files.readAllBytes(cargoStream.getCurrentFilePath()));
        }
    }

    @Test
    void testGetEntityBoundaryShouldReturnCorrectlyCalculatedBoundariesWhenCompressionIsUsed() throws IOException {
        //given
        try (var cargoStream = new BarjCargoArchiverFileOutputStream(BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(out -> Assertions.assertDoesNotThrow(() -> new GzipCompressorOutputStream(out)))
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build())) {
            final var expected = BarjCargoEntryBoundaries.builder()
                    .startChunkName(PREFIX_00001_CARGO)
                    .endChunkName(PREFIX_00001_CARGO)
                    .archivedHash(ZIP_HASH)
                    .archivedSizeBytes(ZIP_CONTENT.length)
                    .originalHash(PLAIN_HASH)
                    .originalSizeBytes(CONTENT.length)
                    .chunkRelativeStartIndexInclusive(0)
                    .chunkRelativeEndIndexExclusive(ZIP_CONTENT.length)
                    .absoluteStartIndexInclusive(0)
                    .absoluteEndIndexExclusive(ZIP_CONTENT.length)
                    .build();
            final var underTest = new ArchiveEntryOutputStream(cargoStream, IoFunction.IDENTITY_OUTPUT_STREAM);

            //when
            underTest.write(CONTENT);
            underTest.close();
            final var actual = underTest.getEntityBoundary();

            //then
            Assertions.assertEquals(expected, actual);
            Assertions.assertArrayEquals(ZIP_CONTENT, Files.readAllBytes(cargoStream.getCurrentFilePath()));
        }
    }

    @Test
    void testGetEntityBoundaryShouldReturnCorrectlyCalculatedBoundariesWhenEncryptionIsUsed() throws IOException {
        //given
        try (var cargoStream = new BarjCargoArchiverFileOutputStream(BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build())) {
            final var expected = BarjCargoEntryBoundaries.builder()
                    .startChunkName(PREFIX_00001_CARGO)
                    .endChunkName(PREFIX_00001_CARGO)
                    .archivedHash(ENCRYPTED_HASH)
                    .archivedSizeBytes(ENCRYPTED_CONTENT.length + IV.length)
                    .originalHash(PLAIN_HASH)
                    .originalSizeBytes(CONTENT.length)
                    .chunkRelativeStartIndexInclusive(0)
                    .chunkRelativeEndIndexExclusive(ENCRYPTED_CONTENT.length + IV.length)
                    .absoluteStartIndexInclusive(0)
                    .absoluteEndIndexExclusive(ENCRYPTED_CONTENT.length + IV.length)
                    .build();
            final var underTest = new ArchiveEntryOutputStream(cargoStream, EncryptionUtil.newCipherOutputStream(SECRET_KEY, IV));

            //when
            underTest.write(CONTENT);
            underTest.close();
            final var actual = underTest.getEntityBoundary();

            //then
            Assertions.assertEquals(expected, actual);
            Assertions.assertArrayEquals(IV_AND_ENCRYPTED_CONTENT, Files.readAllBytes(cargoStream.getCurrentFilePath()));
        }
    }

    @Test
    void testGetEntityBoundaryShouldReturnCorrectlyCalculatedBoundariesWhenCompressionAndEncryptionAreUsed() throws IOException {
        //given
        try (var cargoStream = new BarjCargoArchiverFileOutputStream(BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(out -> Assertions.assertDoesNotThrow(() -> new GzipCompressorOutputStream(out)))
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .build())) {
            final var expected = BarjCargoEntryBoundaries.builder()
                    .startChunkName(PREFIX_00001_CARGO)
                    .endChunkName(PREFIX_00001_CARGO)
                    .archivedHash(ENCRYPTED_ZIP_HASH)
                    .archivedSizeBytes(ENCRYPTED_ZIP_CONTENT.length + IV.length)
                    .originalHash(PLAIN_HASH)
                    .originalSizeBytes(CONTENT.length)
                    .chunkRelativeStartIndexInclusive(0)
                    .chunkRelativeEndIndexExclusive(ENCRYPTED_ZIP_CONTENT.length + IV.length)
                    .absoluteStartIndexInclusive(0)
                    .absoluteEndIndexExclusive(ENCRYPTED_ZIP_CONTENT.length + IV.length)
                    .build();
            final var underTest = new ArchiveEntryOutputStream(cargoStream, EncryptionUtil.newCipherOutputStream(SECRET_KEY, IV));

            //when
            underTest.write(CONTENT);
            underTest.close();
            final var actual = underTest.getEntityBoundary();

            //then
            Assertions.assertEquals(expected, actual);
            Assertions.assertArrayEquals(IV_AND_ENCRYPTED_ZIP_CONTENT, Files.readAllBytes(cargoStream.getCurrentFilePath()));
        }
    }

    @Test
    void testGetEntityBoundaryShouldReturnCorrectlyCalculatedBoundariesWhenEntryIsChunked() throws IOException {
        //given
        final var longContent = new byte[TEN_MEBIBYTE_MINUS_100];
        final var hash = DigestUtils.sha256Hex(longContent);
        try (var cargoStream = new BarjCargoArchiverFileOutputStream(BarjCargoOutputStreamConfiguration.builder()
                .prefix(PREFIX)
                .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM)
                .hashAlgorithm(SHA_256)
                .folder(super.getTestDataRoot())
                .indexEncryptionKey(null)
                .maxFileSizeMebibyte(1)
                .build())) {
            cargoStream.write(CONTENT);
            final var expected = BarjCargoEntryBoundaries.builder()
                    .startChunkName(PREFIX_00001_CARGO)
                    .endChunkName(PREFIX_00010_CARGO)
                    .archivedHash(hash)
                    .archivedSizeBytes(TEN_MEBIBYTE_MINUS_100)
                    .originalHash(hash)
                    .originalSizeBytes(TEN_MEBIBYTE_MINUS_100)
                    .chunkRelativeStartIndexInclusive(CONTENT.length)
                    .chunkRelativeEndIndexExclusive((CONTENT.length + TEN_MEBIBYTE_MINUS_100) % MEBIBYTE)
                    .absoluteStartIndexInclusive(CONTENT.length)
                    .absoluteEndIndexExclusive(CONTENT.length + TEN_MEBIBYTE_MINUS_100)
                    .build();
            final var underTest = new ArchiveEntryOutputStream(cargoStream, IoFunction.IDENTITY_OUTPUT_STREAM);

            //when
            underTest.write(longContent);
            underTest.close();
            final var actual = underTest.getEntityBoundary();

            //then
            Assertions.assertEquals(expected, actual);
        }
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullDestination() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ArchiveEntryOutputStream(null, IoFunction.IDENTITY_OUTPUT_STREAM));

        //then + exception
    }

    @SuppressWarnings({"resource", "DataFlowIssue"})
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNullEncryptionFunction() {
        //given
        final var stream = mock(BarjCargoArchiverFileOutputStream.class);

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ArchiveEntryOutputStream(stream, null));

        //then + exception
    }

    @SuppressWarnings({"LocalCanBeFinal", "SameParameterValue"})
    private static byte[] zipContent(final byte[] content) {
        try (var out = new ByteArrayOutputStream();
             var zip = new GzipCompressorOutputStream(out)) {
            zip.write(content);
            zip.flush();
            zip.close();
            return out.toByteArray();
        } catch (IOException e) {
            Assertions.fail(e.getMessage(), e);
            return new byte[0];
        }
    }

    @SuppressWarnings({"LocalCanBeFinal", "SameParameterValue"})
    private static byte[] encryptContent(
            final byte[] content,
            final SecretKey key,
            final byte[] iv) {
        try (var out = new ByteArrayOutputStream();
             var cipher = new CipherOutputStream(out, EncryptionUtil.createCipher(key, iv, Cipher.ENCRYPT_MODE))) {
            cipher.write(content);
            cipher.flush();
            cipher.close();
            return out.toByteArray();
        } catch (IOException e) {
            Assertions.fail(e.getMessage(), e);
            return new byte[0];
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static byte[] join(
            final byte[] first,
            final byte[] second) {
        final var bytes = new byte[first.length + second.length];
        System.arraycopy(first, 0, bytes, 0, first.length);
        System.arraycopy(second, 0, bytes, first.length, second.length);
        return bytes;
    }
}
