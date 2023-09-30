package com.github.nagyesta.filebarj.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.restore.pipeline.RestoreController;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.job.cli.*;
import com.github.nagyesta.filebarj.job.util.KeyStoreUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.Console;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Main controller of the execution.
 */
@Slf4j
public class Controller {

    private final String[] args;
    private final Console console;

    /**
     * Creates a new {@link Controller} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public Controller(final String[] args, final Console console) {
        this.args = args;
        this.console = console;
    }

    public void run() throws Exception {
        final var result = new CliTaskParser(Arrays.stream(args).limit(1).toArray(String[]::new)).getResult();
        switch (result) {
            case BACKUP:
                final var backupProperties = new CliBackupParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new)).getResult();
                doBackup(backupProperties);
                break;
            case RESTORE:
                final var restoreProperties = new CliRestoreParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doRestore(restoreProperties);
                break;
            case GEN_KEYS:
                final var keyStoreProperties = new CliKeyGenParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doGenerateKey(keyStoreProperties);
                break;
            default:
                throw new IllegalArgumentException("No task found.");
        }
    }

    protected void doGenerateKey(final KeyStoreProperties properties) {
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        KeyStoreUtil.writeKey(
                properties.getKeyStore(), properties.getAlias(), keyPair,
                properties.getPassword(), properties.getPassword());
        log.info("Key pair written to: {}", properties.getKeyStore().toAbsolutePath());
        final var publicKey = KeyStoreUtil.readPublicKey(properties.getKeyStore(), properties.getAlias(), properties.getPassword());
        log.info("Public key: {}", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }

    protected void doRestore(final RestoreProperties properties) {
        final var kek = Optional.ofNullable(properties.getKeyProperties())
                .map(keyStoreProperties -> KeyStoreUtil
                        .readPrivateKey(
                                keyStoreProperties.getKeyStore(), keyStoreProperties.getAlias(),
                                keyStoreProperties.getPassword(), keyStoreProperties.getPassword()))
                .orElse(null);
        final var restoreTargets = new RestoreTargets(
                properties.getTargets().entrySet().stream()
                        .map(entry -> new RestoreTarget(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet())
        );
        log.info("Bootstrapping restore operation...");
        new RestoreController(properties.getBackupSource(), properties.getPrefix(), kek)
                .execute(restoreTargets, properties.getThreads(), properties.isDryRun());
    }

    protected void doBackup(final BackupProperties properties) throws IOException {
        final var config = new ObjectMapper().reader().readValue(properties.getConfig().toFile(), BackupJobConfiguration.class);
        log.info("Bootstrapping backup operation...");
        new BackupController(config, false)
                .execute(properties.getThreads());
    }
}
