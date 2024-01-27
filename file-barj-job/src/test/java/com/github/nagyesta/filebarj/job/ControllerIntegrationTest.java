package com.github.nagyesta.filebarj.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupSource;
import com.github.nagyesta.filebarj.core.config.enums.CompressionAlgorithm;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.job.util.KeyStoreUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Console;
import java.nio.file.Files;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerIntegrationTest extends TempFileAwareTest {

    @Test
    void testEndToEndFlowShouldWorkWhenCalledWithValidParameters() throws Exception {
        //given
        final var password = new char[]{'a', 'b', 'c'};
        final var alias = "alias";
        final var prefix = "prefix";
        final var console = mock(Console.class);
        when(console.readPassword(anyString())).thenReturn(password);
        final var keyStore = testDataRoot.resolve("store.p12");
        final var originalDirectory = testDataRoot.resolve("original");
        Files.createDirectories(originalDirectory);
        final var txtFileName = "file1.txt";
        final var txt = originalDirectory.resolve(txtFileName);
        Files.writeString(txt, "test");
        final var backupDirectory = testDataRoot.resolve("backup");
        final var keyStoreArgs = new String[]{
                "--gen-keys",
                "--key-store", keyStore.toString(),
                "--key-alias", alias
        };

        //when keystore is generated
        new Controller(keyStoreArgs, console).run();

        //given we have a backup configuration
        final var config = testDataRoot.resolve("config.json");
        new ObjectMapper().writeValue(config.toFile(), BackupJobConfiguration.builder()
                .backupType(BackupType.FULL)
                .fileNamePrefix(prefix)
                .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
                .destinationDirectory(backupDirectory)
                .hashAlgorithm(HashAlgorithm.SHA256)
                .compression(CompressionAlgorithm.BZIP2)
                .encryptionKey(KeyStoreUtil.readPublicKey(keyStore, alias, password))
                .sources(Set.of(BackupSource.builder().path(originalDirectory).build()))
                .build());
        final var backupArgs = new String[]{
                "--backup",
                "--config", config.toString()
        };

        //when backup is executed
        new Controller(backupArgs, console).run();

        //given we prepare for restore
        final var restoreDirectory = testDataRoot.resolve("restore");
        final var restoreArgs = new String[]{
                "--restore",
                "--target-mapping", originalDirectory + "=" + restoreDirectory,
                "--backup-source", backupDirectory.toString(),
                "--prefix", prefix,
                "--key-store", keyStore.toString(),
                "--key-alias", alias
        };

        //when restore is executed
        new Controller(restoreArgs, console).run();

        //then the file exists in the restore directory
        final var restoredTxt = restoreDirectory.resolve(txtFileName);
        Assertions.assertTrue(Files.exists(restoredTxt));
        Assertions.assertEquals(Files.readString(txt), Files.readString(restoredTxt));

        //given we inspect the versions
        final var inspectIncrementsArgs = new String[]{
                "--inspect-increments",
                "--backup-source", backupDirectory.toString(),
                "--prefix", prefix,
                "--key-store", keyStore.toString(),
                "--key-alias", alias
        };

        //when inspect increments is executed
        new Controller(inspectIncrementsArgs, console).run();

        //given we inspect the content of the backup
        final var contentCsv = testDataRoot.resolve("backup-content.csv");
        final var inspectContentArgs = new String[]{
                "--inspect-content",
                "--backup-source", backupDirectory.toString(),
                "--prefix", prefix,
                "--key-store", keyStore.toString(),
                "--key-alias", alias,
                "--output-file", contentCsv.toString(),
                "--at-epoch-seconds", Long.MAX_VALUE + ""
        };

        //when inspect content is executed
        new Controller(inspectContentArgs, console).run();

        //then
        final var actualContent = Files.readAllLines(contentCsv);
        Assertions.assertTrue(actualContent.get(0).contains("hash_sha256"));
        Assertions.assertTrue(actualContent.get(1).endsWith(originalDirectory.toAbsolutePath().toString()));
        Assertions.assertTrue(actualContent.get(2).endsWith(txt.toAbsolutePath().toString()));
    }
}
