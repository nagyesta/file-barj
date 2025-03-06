package com.github.nagyesta.filebarj.core.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.AppVersion;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.ValidationRules;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import com.github.nagyesta.filebarj.core.util.OsUtil;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.LOAD_MANIFESTS;

/**
 * Default implementation of {@link ManifestManager}.
 */
@Slf4j
public class ManifestManagerImpl implements ManifestManager {
    private static final String HISTORY_FOLDER = ".history";
    private static final String MANIFEST_JSON_GZ = ".manifest.json.gz";
    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = createValidator();
    private final ProgressTracker progressTracker;

    public ManifestManagerImpl(final @NonNull ProgressTracker progressTracker) {
        progressTracker.assertSupports(LOAD_MANIFESTS);
        this.progressTracker = progressTracker;
    }

    @Override
    public BackupIncrementManifest generateManifest(
            final @NonNull BackupJobConfiguration jobConfiguration,
            final @NonNull BackupType backupTypeOverride,
            final int nextVersion) {
        final var startTimeEpochSecond = Instant.now().getEpochSecond();
        final var fileNamePrefix = jobConfiguration.getFileNamePrefix() + "-" + startTimeEpochSecond;
        final var manifest = BackupIncrementManifest.builder()
                .appVersion(new AppVersion())
                .configuration(jobConfiguration)
                .backupType(backupTypeOverride)
                .versions(new TreeSet<>(Set.of(nextVersion)))
                .files(new ConcurrentHashMap<>())
                .archivedEntries(new ConcurrentHashMap<>())
                .startTimeUtcEpochSeconds(startTimeEpochSecond)
                .fileNamePrefix(fileNamePrefix)
                .operatingSystem(OsUtil.getRawOsName())
                .build();
        Optional.ofNullable(jobConfiguration.getEncryptionKey())
                .ifPresent(manifest::generateDataEncryptionKeys);
        validate(manifest, ValidationRules.Created.class);
        return manifest;
    }

    @Override
    public void persist(
            final @NonNull BackupIncrementManifest manifest) {
        final var backupDestination = manifest.getConfiguration().getDestinationDirectory();
        persist(manifest, backupDestination);
    }

    @Override
    public void persist(
            final @NonNull BackupIncrementManifest manifest,
            final @NonNull Path backupDestination) {
        validate(manifest, ValidationRules.Persisted.class);
        doPersist(manifest, backupDestination.toFile());
    }

    private void doPersist(
            final @NotNull BackupIncrementManifest manifest,
            final @NotNull File backupDestination) {
        final var backupHistoryDir = new File(backupDestination, HISTORY_FOLDER);
        //noinspection ResultOfMethodCallIgnored
        backupHistoryDir.mkdirs();
        final var plainManifestFile = new File(backupHistoryDir, manifest.getFileNamePrefix() + MANIFEST_JSON_GZ);
        try (var fileStream = new FileOutputStream(plainManifestFile);
             var bufferedStream = new BufferedOutputStream(fileStream);
             var gzipStream = new GZIPOutputStream(bufferedStream);
             var writer = new OutputStreamWriter(gzipStream, StandardCharsets.UTF_8)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, manifest);
        } catch (final Exception e) {
            throw new ArchivalException("Failed to save manifest.", e);
        }
        final var encryptedManifestFile = backupDestination.toPath()
                .resolve(manifest.getFileNamePrefix() + ".manifest.cargo").toFile();
        final var publicKey = manifest.getConfiguration().getEncryptionKey();
        try (var fileIn = new FileInputStream(plainManifestFile);
             var bufferedIn = new BufferedInputStream(fileIn);
             var fileOut = new FileOutputStream(encryptedManifestFile);
             var bufferedOut = new BufferedOutputStream(fileOut);
             var encryptionOut = new ManifestCipherOutputStream(bufferedOut, publicKey)) {
            bufferedIn.transferTo(encryptionOut);
        } catch (final Exception e) {
            throw new ArchivalException("Failed to encrypt manifest.", e);
        }
    }

    @Override
    public ManifestDatabase load(
            final @NonNull Path destinationDirectory,
            final @NonNull String fileNamePrefix,
            final @Nullable PrivateKey privateKey,
            final long latestBeforeEpochMillis) {
        final var manifestDatabase = ManifestDatabase.newInstance();
        try (var pathStream = Files.list(destinationDirectory)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".manifest.cargo"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            loadManifests(manifestDatabase, manifestFiles, privateKey, latestBeforeEpochMillis);
            verifyThatAllIncrementsAreFound(manifestDatabase);
            return manifestDatabase;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public ManifestDatabase loadAll(
            final @NonNull Path destinationDirectory,
            final @NonNull String fileNamePrefix,
            final @Nullable PrivateKey privateKey) {
        final var manifestDatabase = ManifestDatabase.newInstance();
        try (var pathStream = Files.list(destinationDirectory)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".manifest.cargo"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            loadAllManifests(manifestDatabase, manifestFiles, privateKey);
            return manifestDatabase;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public ManifestDatabase loadPreviousManifestsForBackup(
            final @NonNull BackupJobConfiguration job) {
        final var manifestDatabase = ManifestDatabase.newInstance();
        final var historyFolder = job.getDestinationDirectory().resolve(HISTORY_FOLDER);
        if (!Files.exists(historyFolder)) {
            return manifestDatabase;
        }
        try (var pathStream = Files.list(historyFolder)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(job.getFileNamePrefix()))
                    .filter(path -> path.getFileName().toString().endsWith(MANIFEST_JSON_GZ))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            loadManifests(manifestDatabase, manifestFiles, null, Long.MAX_VALUE);
            verifyThatAllIncrementsAreFound(manifestDatabase);
            if (!manifestDatabase.isEmpty() && !job.equals(manifestDatabase.getLatestConfiguration())) {
                log.warn("The provided job configuration changed since the last backup. Falling back to FULL backup.");
                manifestDatabase.clear();
            }
            return manifestDatabase;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public void validate(
            final @NonNull BackupIncrementManifest manifest,
            final @NonNull Class<? extends ValidationRules> forAction) {

        final var violations = validator.validate(manifest, forAction, Default.class);
        if (!violations.isEmpty()) {
            final var violationsMessage = violations.stream()
                    .map(v -> v.getPropertyPath().toString() + ": " + v.getMessage() + " (found: " + v.getInvalidValue() + ")")
                    .collect(Collectors.joining("\n\t"));
            log.error("Manifest validation failed for {} action:\n\t{}", forAction.getSimpleName(), violationsMessage);
            throw new ValidationException("The manifest is invalid!");
        }
    }

    @Override
    public void deleteIncrement(
            final @NonNull Path backupDirectory,
            final @NonNull String fileNamePrefix) {
        deleteManifestFromHistoryIfExists(backupDirectory, fileNamePrefix);
        deleteManifestAndArchiveFilesFromBackupDirectory(backupDirectory, fileNamePrefix);
    }

    private static Validator createValidator() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            return validatorFactory.getValidator();
        }
    }

    private void loadManifests(
            final @NotNull ManifestDatabase manifestDatabase,
            final @NotNull List<Path> manifestFiles,
            final @Nullable PrivateKey privateKey,
            final long latestBeforeEpochMillis) {
        progressTracker.estimateStepSubtotal(LOAD_MANIFESTS, manifestFiles.size());
        for (final var path : manifestFiles) {
            try (var fileStream = new FileInputStream(path.toFile());
                 var bufferedStream = new BufferedInputStream(fileStream);
                 var cryptoStream = new ManifestCipherInputStream(bufferedStream, privateKey);
                 var gzipStream = new GZIPInputStream(cryptoStream);
                 var reader = new InputStreamReader(gzipStream, StandardCharsets.UTF_8)) {
                final var manifest = mapper.readerFor(BackupIncrementManifest.class)
                        .readValue(reader, BackupIncrementManifest.class);
                if (manifest.getStartTimeUtcEpochSeconds() > latestBeforeEpochMillis) {
                    continue;
                }
                validate(manifest, ValidationRules.Persisted.class);
                final var manifestId = manifestDatabase.persistIncrement(manifest);
                manifest.getFiles().values().forEach(fileMetadata -> manifestDatabase
                        .persistFileMetadata(manifestId, fileMetadata));
                manifest.getArchivedEntries().values().forEach(archivedEntry -> manifestDatabase
                        .persistArchiveMetadata(manifestId, archivedEntry));
                if (manifest.getBackupType() == BackupType.FULL) {
                    break;
                }
            } catch (final Exception e) {
                log.warn("Failed to load manifest file: {}", path, e);
            }
            progressTracker.recordProgressInSubSteps(LOAD_MANIFESTS);
        }
        progressTracker.completeStep(LOAD_MANIFESTS);
    }

    private void loadAllManifests(
            final @NotNull ManifestDatabase manifestDatabase,
            final @NotNull List<Path> manifestFiles,
            final @Nullable PrivateKey privateKey) {
        progressTracker.estimateStepSubtotal(LOAD_MANIFESTS, manifestFiles.size());
        for (final var path : manifestFiles) {
            try (var fileStream = new FileInputStream(path.toFile());
                 var bufferedStream = new BufferedInputStream(fileStream);
                 var cryptoStream = new ManifestCipherInputStream(bufferedStream, privateKey);
                 var gzipStream = new GZIPInputStream(cryptoStream);
                 var reader = new InputStreamReader(gzipStream, StandardCharsets.UTF_8)) {
                final var manifest = mapper.readerFor(BackupIncrementManifest.class)
                        .readValue(reader, BackupIncrementManifest.class);
                validate(manifest, ValidationRules.Persisted.class);
                manifestDatabase.persistIncrement(manifest);
                progressTracker.recordProgressInSubSteps(LOAD_MANIFESTS);
            } catch (final Exception e) {
                log.warn("Failed to load manifest file: {}", path, e);
            }
        }
        if (manifestDatabase.isEmpty()) {
            throw new ArchivalException("No manifests found.");
        }

        progressTracker.completeStep(LOAD_MANIFESTS);
    }

    private void verifyThatAllIncrementsAreFound(@NotNull final ManifestDatabase manifestDatabase) {
        final var expectedVersions = IntStream.range(0, manifestDatabase.nextIncrement())
                .boxed().collect(Collectors.toSet());
        final var actualVersions = manifestDatabase.getAllVersionIncrements();
        if (!expectedVersions.equals(actualVersions)) {
            final var notFound = new TreeSet<>(expectedVersions);
            notFound.removeAll(actualVersions);
            final var notWanted = new TreeSet<>(actualVersions);
            notWanted.removeAll(expectedVersions);
            if (!notFound.isEmpty()) {
                log.error("Expected manifest version but not found: {}", notFound);
            }
            if (!notWanted.isEmpty()) {
                log.error("Found manifest version but not expected: {}", notWanted);
            }
            throw new ArchivalException("The manifest versions do not match the expected versions.");
        }
    }

    private void deleteManifestAndArchiveFilesFromBackupDirectory(
            final @NotNull Path backupDirectory, final @NotNull String fileNamePrefix) {
        final var patterns = Set.of(
                "^" + fileNamePrefix + "\\.[0-9]{5}\\.cargo$",
                "^" + fileNamePrefix + "\\.manifest\\.cargo$",
                "^" + fileNamePrefix + "\\.index\\.cargo$"
        );
        try (var list = Files.list(backupDirectory)) {
            final var toDelete = new ArrayList<Path>();
            list.filter(path -> patterns.stream().anyMatch(pattern -> path.getFileName().toString().matches(pattern)))
                    .forEach(toDelete::add);
            for (final var path : toDelete) {
                log.info("Deleting obsolete backup file: {}", path);
                try {
                    Files.delete(path);
                } catch (final IOException e) {
                    log.warn("Unable to delete file! Will attempt to delete it on exit.", e);
                    if (Files.exists(path)) {
                        path.toFile().deleteOnExit();
                    }
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteManifestFromHistoryIfExists(
            final @NotNull Path backupDirectory, final @NotNull String fileNamePrefix) {
        final var fromHistory = backupDirectory.resolve(HISTORY_FOLDER)
                .resolve(fileNamePrefix + MANIFEST_JSON_GZ);
        try {
            if (Files.exists(fromHistory)) {
                log.info("Deleting obsolete file from history: {}", fromHistory);
                Files.delete(fromHistory);
            }
        } catch (final IOException e) {
            log.error("Could not delete manifest file from history folder: {}", fromHistory, e);
        }
    }
}
