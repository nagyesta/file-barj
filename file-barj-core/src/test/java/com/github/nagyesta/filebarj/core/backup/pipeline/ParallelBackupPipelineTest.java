package com.github.nagyesta.filebarj.core.backup.pipeline;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class ParallelBackupPipelineTest extends TempFileAwareTest {

    @SuppressWarnings({"DataFlowIssue", "resource"})
    @Test
    void testParallelConstructorShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ParallelBackupPipeline(null, 1));

        //then + exception
    }

    @SuppressWarnings({"DataFlowIssue"})
    @Test
    void testStoreEntriesShouldThrowExceptionWhenCalledWithNull() throws IOException {
        //given
        final var manifest = getManifest();
        final var underTest = new ParallelBackupPipeline(manifest, 1);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.storeEntries(null));

        //then + exception
    }

    @Test
    void testStoreEntriesShouldThrowExceptionWhenCalledWithANullEntryInTheList() throws IOException {
        //given
        final var manifest = getManifest();
        final var underTest = new ParallelBackupPipeline(manifest, 1);
        final var list = new ArrayList<List<FileMetadata>>();
        list.add(null);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.storeEntries(list));

        //then + exception
    }

    @Test
    void testStoreEntriesShouldThrowExceptionWhenCalledWithAnEmptyListEntryInTheList() throws IOException {
        //given
        final var manifest = getManifest();
        final var underTest = new ParallelBackupPipeline(manifest, 1);
        final var list = new ArrayList<List<FileMetadata>>();
        list.add(List.of());

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.storeEntries(list));

        //then + exception
    }

    private ManifestDatabase getManifest() {
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
        final var backupController = new BackupController(parameters);
        return backupController.getManifestDatabase();
    }
}
