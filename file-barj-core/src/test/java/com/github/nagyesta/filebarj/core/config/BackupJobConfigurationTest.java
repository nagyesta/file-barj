package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

class BackupJobConfigurationTest extends TempFileAwareTest {

    @SuppressWarnings("resource")
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfFullyPopulatedObject() throws JsonProcessingException {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var expected = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.GZIP)
                .encryptionKey(EncryptionUtil.generateRsaKeyPair().getPublic())
                .chunkSizeMebibyte(1024)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupJobConfiguration actual = objectMapper.readerFor(BackupJobConfiguration.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.getBackupType(), actual.getBackupType());
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
        Assertions.assertEquals(expected.getChunkSizeMebibyte(), actual.getChunkSizeMebibyte());
        Assertions.assertIterableEquals(expected.getSources(), actual.getSources());
    }

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedStateOfMinimalObject() throws JsonProcessingException {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var expected = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final BackupJobConfiguration actual = objectMapper.readerFor(BackupJobConfiguration.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(expected.getBackupType(), actual.getBackupType());
        Assertions.assertEquals(expected.hashCode(), actual.hashCode());
        Assertions.assertEquals(expected.getChunkSizeMebibyte(), actual.getChunkSizeMebibyte());
        Assertions.assertIterableEquals(expected.getSources(), actual.getSources());
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testValidationShouldPassWhenCalledOnFullyPopulatedObject() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.GZIP)
                .encryptionKey(EncryptionUtil.generateRsaKeyPair().getPublic())
                .chunkSizeMebibyte(1024)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(0, actual.size());
    }

    @Test
    void testValidationShouldPassWhenCalledOnMinimalObject() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(0, actual.size());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithEmptyPrefix() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("")
                .chunkSizeMebibyte(1)
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(2, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualFirstEntry = actual.stream().min(Comparator.comparing(ConstraintViolation::getMessage)).get();
        Assertions.assertEquals("fileNamePrefix", actualFirstEntry.getPropertyPath().toString());
        Assertions.assertEquals("", actualFirstEntry.getInvalidValue());
        Assertions.assertEquals("must match \"^[a-zA-Z0-9_-]+$\"", actualFirstEntry.getMessage());
        //noinspection OptionalGetWithoutIsPresent
        final var actualSecondEntry = actual.stream().max(Comparator.comparing(ConstraintViolation::getMessage)).get();
        Assertions.assertEquals("fileNamePrefix", actualSecondEntry.getPropertyPath().toString());
        Assertions.assertEquals("", actualSecondEntry.getInvalidValue());
        Assertions.assertEquals("must not be blank", actualSecondEntry.getMessage());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithInvalidPrefix() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("i n v a l i d")
                .chunkSizeMebibyte(1)
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(1, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualEntry = actual.stream().findFirst().get();
        Assertions.assertEquals("fileNamePrefix", actualEntry.getPropertyPath().toString());
        Assertions.assertEquals("i n v a l i d", actualEntry.getInvalidValue());
        Assertions.assertEquals("must match \"^[a-zA-Z0-9_-]+$\"", actualEntry.getMessage());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithInvalidChunkSize() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .chunkSizeMebibyte(0)
                .sources(Set.of(BackupSource.builder().path(BackupPath.of(testDataRoot, "visible-file1.txt")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(1, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualEntry = actual.stream().findFirst().get();
        Assertions.assertEquals("chunkSizeMebibyte", actualEntry.getPropertyPath().toString());
        Assertions.assertEquals(0, actualEntry.getInvalidValue());
        Assertions.assertEquals("must be greater than 0", actualEntry.getMessage());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithNoSources() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .chunkSizeMebibyte(1)
                .sources(Set.of())
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(1, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualEntry = actual.stream().findFirst().get();
        Assertions.assertEquals("sources", actualEntry.getPropertyPath().toString());
        Assertions.assertEquals(Set.of(), actualEntry.getInvalidValue());
        Assertions.assertEquals("size must be between 1 and 2147483647", actualEntry.getMessage());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithInvalidSourcePath() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .chunkSizeMebibyte(1)
                .sources(Set.of(BackupSource.builder().path(BackupPath.ofPathAsIs("")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(1, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualEntry = actual.stream().findFirst().get();
        Assertions.assertEquals("sources[].path.path", actualEntry.getPropertyPath().toString());
        Assertions.assertEquals("", actualEntry.getInvalidValue());
        Assertions.assertEquals("must not be blank", actualEntry.getMessage());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithInvalidSourceIncludePattern() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .chunkSizeMebibyte(1)
                .sources(Set.of(BackupSource.builder()
                        .path(BackupPath.of(testDataRoot))
                        .includePatterns(Set.of("")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(1, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualEntry = actual.stream().findFirst().get();
        Assertions.assertEquals("sources[].includePatterns[].<iterable element>", actualEntry.getPropertyPath().toString());
        Assertions.assertEquals("", actualEntry.getInvalidValue());
        Assertions.assertEquals("must not be blank", actualEntry.getMessage());
    }

    @Test
    void testValidationShouldFailWhenCalledOnConfigurationWithInvalidSourceExcludePattern() {
        //given
        final var testRoot = testDataRoot.toAbsolutePath().toString();
        final var underTest = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .hashAlgorithm(HashAlgorithm.NONE)
                .compression(CompressionAlgorithm.NONE)
                .destinationDirectory(Path.of(testRoot, "file-barj"))
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .fileNamePrefix("backup-")
                .chunkSizeMebibyte(1)
                .sources(Set.of(BackupSource.builder()
                        .path(BackupPath.of(testDataRoot))
                        .excludePatterns(Set.of("")).build()))
                .build();

        //when
        final var actual = validator.validate(underTest);

        //then
        Assertions.assertEquals(1, actual.size());
        //noinspection OptionalGetWithoutIsPresent
        final var actualEntry = actual.stream().findFirst().get();
        Assertions.assertEquals("sources[].excludePatterns[].<iterable element>", actualEntry.getPropertyPath().toString());
        Assertions.assertEquals("", actualEntry.getInvalidValue());
        Assertions.assertEquals("must not be blank", actualEntry.getMessage());
    }
}
