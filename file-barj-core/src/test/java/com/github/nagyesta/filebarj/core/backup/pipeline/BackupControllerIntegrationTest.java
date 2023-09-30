package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Collectors;

class BackupControllerIntegrationTest extends TempFileAwareTest {

    private static final String IMG_PNG = "01-img.png";
    private static final String IMG_LINK_PNG = "02-img-link.png";

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void testExecuteShouldCreateValidBackupWhenCalledWithKnownInputData(final int threads) throws IOException {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();

        final var sourceDirectory = Path.of(testDataRoot.toString(), "source");
        final var image = new File(sourceDirectory.toString(), IMG_PNG);
        final var exampleFile = getExampleFile();
        FileUtils.copyFile(exampleFile, image);
        final var imageLink = new File(sourceDirectory.toString(), IMG_LINK_PNG);
        Files.createSymbolicLink(imageLink.toPath(), exampleFile.toPath());

        final var job = getConfiguration(BackupType.FULL, keyPair, sourceDirectory);
        final var underTest = new BackupController(job, false);

        //when
        underTest.execute(threads);
        final var actualManifest = underTest.getManifest();

        //then
        final var indexName = actualManifest.getIndexFileName();
        final var indexFile = new File(job.getDestinationDirectory().toString(), indexName);
        Assertions.assertTrue(indexFile.exists());
        final var indexBytes = Files.readAllBytes(indexFile.toPath());
        final var indexDecryptionKey = actualManifest.dataIndexDecryptionKey(keyPair.getPrivate());
        final var indexDecrypted = decrypt(indexBytes, 0, indexBytes.length, indexDecryptionKey);
        final var indexProperties = new Properties();
        indexProperties.load(new ByteArrayInputStream(Objects.requireNonNull(indexDecrypted)));
        //verify the archive contains the expected files
        final var dirName = indexProperties.get("00000001.path");
        final var name1 = indexProperties.get("00000002.path");
        final var name2 = indexProperties.get("00000003.path");
        final var expectedFileNames = actualManifest.getFiles().values().stream()
                .map(FileMetadata::getArchiveMetadataId)
                .filter(Objects::nonNull)
                .map(uuid -> "/0/" + uuid)
                .collect(Collectors.toCollection(TreeSet::new));
        final var actualFileNames = new TreeSet<>(Set.of(name1, name2));
        Assertions.assertEquals("/0", dirName);
        Assertions.assertIterableEquals(expectedFileNames, actualFileNames);
        //verify the archive contains no more files
        Assertions.assertFalse(indexProperties.containsKey("00000004.path"));

        //read data file content
        final var fileNames = actualManifest.getDataFileNames();
        Assertions.assertEquals(1, fileNames.size());
        final var dataFile = new File(job.getDestinationDirectory().toString(), fileNames.get(0));
        Assertions.assertTrue(dataFile.exists());
        final var dataBytes = Files.readAllBytes(dataFile.toPath());
        //verify file contents
        final var indexMap = Map.of(name1, "00000002", name2, "00000003");
        for (final var entry : actualManifest.getFiles().entrySet()) {
            final var v = entry.getValue();
            final var id = v.getArchiveMetadataId();
            if (id == null) {
                continue;
            }
            final var idx = indexMap.get("/0/" + id);
            final var start = Integer.parseInt(String.valueOf(indexProperties.get(idx + ".content.abs.start.idx")));
            final var end = Integer.parseInt(String.valueOf(indexProperties.get(idx + ".content.abs.end.idx")));
            final var locator = Objects.requireNonNull(ArchiveEntryLocator.fromEntryPath("/0/" + id));
            final var key = actualManifest.dataDecryptionKey(keyPair.getPrivate(), locator);
            final var fileContent = decrypt(dataBytes, start, end - start, key);
            final byte[] originalBytes;
            if (v.getFileType() == FileType.SYMBOLIC_LINK) {
                originalBytes = exampleFile.getAbsolutePath().getBytes();
            } else {
                originalBytes = Files.readAllBytes(v.getAbsolutePath());
            }
            Assertions.assertArrayEquals(originalBytes, fileContent);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 0})
    void testExecuteShouldThrowExceptionWhenCalledWithZeroOrLess(final int threads) throws IOException {
        //given
        final var sourceDirectory = Path.of(testDataRoot.toString(), "source");
        final var image = new File(sourceDirectory.toString(), IMG_PNG);
        final var exampleFile = getExampleFile();
        FileUtils.copyFile(exampleFile, image);
        final var imageLink = new File(sourceDirectory.toString(), IMG_LINK_PNG);
        Files.createSymbolicLink(imageLink.toPath(), exampleFile.toPath());
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var job = getConfiguration(BackupType.FULL, keyPair, sourceDirectory);
        final var underTest = new BackupController(job, false);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.execute(threads));

        //then + exception
    }

    @Test
    void testConstructorShouldDefaultToFullBackupWhenCalledWithZeroManifests() throws IOException {
        //given
        final var sourceDirectory = Path.of(testDataRoot.toString(), "source");
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var job = getConfiguration(BackupType.INCREMENTAL, keyPair, sourceDirectory);

        //when
        final var underTest = new BackupController(job, false);
        final var actual = underTest.getManifest().getBackupType();

        //then
        Assertions.assertEquals(BackupType.FULL, actual);
    }

    private BackupJobConfiguration getConfiguration(
            final BackupType backupType,
            final KeyPair keyPair,
            final Path sourceDirectory) {
        final var destinationDirectory = Path.of(testDataRoot.toString(), "destination");
        final var backupSource = BackupSource.builder()
                .path(sourceDirectory)
                .build();
        return BackupJobConfiguration.builder()
                .backupType(backupType)
                .fileNamePrefix("test")
                .compression(CompressionAlgorithm.BZIP2)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(destinationDirectory)
                .encryptionKey(keyPair.getPublic())
                .sources(Set.of(backupSource))
                .build();
    }

    private byte[] decrypt(final byte[] bytes, final int offset, final int length, final SecretKey secretKey) {
        final var iv = new byte[EncryptionUtil.GCM_IV_BYTES];
        System.arraycopy(bytes, offset, iv, 0, EncryptionUtil.GCM_IV_BYTES);
        final var cipher = EncryptionUtil.createCipher(secretKey, iv, Cipher.DECRYPT_MODE);
        final var ivAdjustedOffset = offset + EncryptionUtil.GCM_IV_BYTES;
        final var ivAdjustedLength = length - EncryptionUtil.GCM_IV_BYTES;
        try (var stream = new ByteArrayInputStream(bytes, ivAdjustedOffset, ivAdjustedLength);
             var cipherStream = new CipherInputStream(stream, cipher);
             var bzipStream = new BZip2CompressorInputStream(cipherStream);
             var outputStream = new ByteArrayOutputStream()) {
            IOUtils.copy(bzipStream, outputStream);
            return outputStream.toByteArray();
        } catch (final IOException e) {
            Assertions.fail(e.getMessage(), e);
            return null;
        }
    }

    private File getExampleFile() {
        return new File(Objects.requireNonNull(getClass()
                .getResource("/encrypt/FileBarJ-logo-512_decrypted.png")).getFile());
    }
}
