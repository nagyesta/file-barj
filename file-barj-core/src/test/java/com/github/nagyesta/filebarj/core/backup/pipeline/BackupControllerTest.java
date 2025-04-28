package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class BackupControllerTest extends TempFileAwareTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BackupController(null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderShouldThrowExceptionWhenCalledWithNullJob() {
        //given
        final var builder = BackupParameters.builder();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.job(null));

        //then + exception
    }

    @Test
    void testConstructorShouldGenerateManifestWhenCalledWithValidInput() {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        final var backupSource = BackupSource.builder()
                .path(BackupPath.of(testDataRoot, "test"))
                .build();
        final var job = BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .fileNamePrefix("test")
                .compression(CompressionAlgorithm.BZIP2)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(testDataRoot)
                .encryptionKey(keyPair.getPublic())
                .sources(Set.of(backupSource))
                .build();
        final var parameters = BackupParameters.builder()
                .job(job)
                .forceFull(false)
                .build();

        //when
        final var underTest = new BackupController(parameters);
        final var actual = underTest.getManifest();

        //then
        Assertions.assertEquals(BackupType.FULL, actual.getBackupType());
        Assertions.assertEquals(job, actual.getConfiguration());
        Assertions.assertIterableEquals(Set.of(0), actual.getVersions());
        Assertions.assertTrue(actual.getFileNamePrefix().startsWith(job.getFileNamePrefix()));
        Assertions.assertEquals(BackupIncrementManifest.DEK_COUNT, actual.getEncryptionKeys().get(0).size());
        Assertions.assertEquals(0, actual.getArchivedEntries().size());
        Assertions.assertEquals(0, actual.getFiles().size());
    }
}
