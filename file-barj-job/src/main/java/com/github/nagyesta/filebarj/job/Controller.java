package com.github.nagyesta.filebarj.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.backup.pipeline.BackupController;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.RestoreTarget;
import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.config.RestoreTask;
import com.github.nagyesta.filebarj.core.delete.IncrementDeletionController;
import com.github.nagyesta.filebarj.core.inspect.pipeline.IncrementInspectionController;
import com.github.nagyesta.filebarj.core.merge.MergeController;
import com.github.nagyesta.filebarj.core.restore.pipeline.RestoreController;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.job.cli.*;
import com.github.nagyesta.filebarj.job.util.KeyStoreUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.Console;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.util.TimerUtil.toProcessSummary;

/**
 * Main controller of the execution.
 */
@Slf4j
public class Controller {

    private static final String RESET = "\033[0;0m";
    private static final String GREEN = "\033[0;32m";
    private static final String BLUE = "\033[0;34m";
    private static final String BLACK = "\033[0;30m";
    private static final String WHITE = "\033[0;37";
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
        printBanner();
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
            case MERGE:
                final var mergeProperties = new CliMergeParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doMerge(mergeProperties);
                break;
            case GEN_KEYS:
                final var keyStoreProperties = new CliKeyGenParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doGenerateKey(keyStoreProperties);
                break;
            case INSPECT_CONTENT:
                final var inspectContentProperties = new CliInspectContentParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doInspectContent(inspectContentProperties);
                break;
            case INSPECT_INCREMENTS:
                final var inspectIncrementsProperties = new CliInspectIncrementsParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doInspectIncrements(inspectIncrementsProperties);
                break;
            case DELETE_INCREMENTS:
                final var deleteIncrementsProperties = new CliDeleteIncrementsParser(Arrays.stream(args)
                        .skip(1)
                        .toArray(String[]::new), console).getResult();
                doDeleteIncrements(deleteIncrementsProperties);
                break;
            default:
                throw new IllegalArgumentException("No task found.");
        }
    }

    protected void doInspectContent(final InspectIncrementContentsProperties properties) {
        final var kek = getPrivateKey(properties.getKeyProperties());
        final var startTimeMillis = System.currentTimeMillis();
        log.info("Bootstrapping inspect content operation...");
        final var pointInTimeEpochSeconds = properties.getPointInTimeEpochSeconds();
        new IncrementInspectionController(properties.getBackupSource(), properties.getPrefix(), kek)
                .inspectContent(pointInTimeEpochSeconds, properties.getOutputFile());
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Increment content inspection operation completed. Total time: {}", toProcessSummary(durationMillis));
    }

    protected void doInspectIncrements(final InspectIncrementsProperties properties) {
        final var kek = getPrivateKey(properties.getKeyProperties());
        final var startTimeMillis = System.currentTimeMillis();
        log.info("Bootstrapping inspect increments operation...");
        new IncrementInspectionController(properties.getBackupSource(), properties.getPrefix(), kek)
                .inspectIncrements(System.out);
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Backup increments inspection operation completed. Total time: {}", toProcessSummary(durationMillis));
    }

    protected void doDeleteIncrements(final DeleteIncrementsProperties properties) {
        final var kek = getPrivateKey(properties.getKeyProperties());
        final var startTimeMillis = System.currentTimeMillis();
        log.info("Bootstrapping delete increments operation...");
        new IncrementDeletionController(properties.getBackupSource(), properties.getPrefix(), kek)
                .deleteIncrementsUntilNextFullBackupAfter(properties.getAfterEpochSeconds());
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Increment deletion operation completed. Total time: {}", toProcessSummary(durationMillis));
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
        final var kek = getPrivateKey(properties.getKeyProperties());
        final var restoreTargets = new RestoreTargets(
                properties.getTargets().entrySet().stream()
                        .map(entry -> new RestoreTarget(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet())
        );
        final var startTimeMillis = System.currentTimeMillis();
        log.info("Bootstrapping restore operation...");
        final var restoreTask = RestoreTask.builder()
                .restoreTargets(restoreTargets)
                .threads(properties.getThreads())
                .dryRun(properties.isDryRun())
                .deleteFilesNotInBackup(properties.isDeleteFilesNotInBackup())
                .includedPath(properties.getIncludedPath())
                .permissionComparisonStrategy(properties.getPermissionComparisonStrategy())
                .build();
        new RestoreController(properties.getBackupSource(), properties.getPrefix(), kek, properties.getPointInTimeEpochSeconds())
                .execute(restoreTask);
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Restore operation completed. Total time: {}", toProcessSummary(durationMillis));
    }

    protected void doMerge(final MergeProperties properties) {
        final var kek = getPrivateKey(properties.getKeyProperties());
        final var startTimeMillis = System.currentTimeMillis();
        log.info("Bootstrapping merge operation...");
        new MergeController(properties.getBackupSource(), properties.getPrefix(), kek,
                properties.getFromTimeEpochSeconds(), properties.getToTimeEpochSeconds())
                .execute(properties.isDeleteObsoleteFiles());
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Merge operation completed. Total time: {}", toProcessSummary(durationMillis));
    }

    protected void doBackup(final BackupProperties properties) throws IOException {
        final var config = new ObjectMapper().reader().readValue(properties.getConfig().toFile(), BackupJobConfiguration.class);
        final var startTimeMillis = System.currentTimeMillis();
        log.info("Bootstrapping backup operation...");
        new BackupController(config, properties.isForceFullBackup())
                .execute(properties.getThreads());
        final var endTimeMillis = System.currentTimeMillis();
        final var durationMillis = (endTimeMillis - startTimeMillis);
        log.info("Backup operation completed. Total time: {}", toProcessSummary(durationMillis));
    }

    private void printBanner() throws IOException {
        final var bannerBytes = Objects.requireNonNull(Controller.class.getResourceAsStream("/banner.txt")).readAllBytes();
        new String(bannerBytes)
                .replaceAll("3", WHITE)
                .replaceAll("4", GREEN)
                .replaceAll("5", BLUE)
                .replaceAll("6", BLACK)
                .replaceAll("7", RESET).lines()
                .forEach(System.out::println);
    }

    @Nullable
    private PrivateKey getPrivateKey(final KeyStoreProperties keyProperties) {
        return Optional.ofNullable(keyProperties)
                .map(keyStoreProperties -> KeyStoreUtil
                        .readPrivateKey(
                                keyStoreProperties.getKeyStore(), keyStoreProperties.getAlias(),
                                keyStoreProperties.getPassword(), keyStoreProperties.getPassword()))
                .orElse(null);
    }
}
