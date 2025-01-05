package com.github.nagyesta.filebarj.core.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.OperatingSystem;
import com.github.nagyesta.filebarj.core.progress.ProgressTracker;
import com.github.nagyesta.filebarj.core.util.LogUtil;
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
import java.util.function.Function;
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
    public SortedMap<Integer, BackupIncrementManifest> load(
            final @NonNull Path destinationDirectory,
            final @NonNull String fileNamePrefix,
            final @Nullable PrivateKey privateKey,
            final long latestBeforeEpochMillis) {
        try (var pathStream = Files.list(destinationDirectory)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".manifest.cargo"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            final var manifests = loadManifests(manifestFiles, privateKey, latestBeforeEpochMillis);
            verifyThatAllIncrementsAreFound(manifests);
            return manifests;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public SortedMap<Long, BackupIncrementManifest> loadAll(
            final @NonNull Path destinationDirectory,
            final @NonNull String fileNamePrefix,
            final @Nullable PrivateKey privateKey) {
        try (var pathStream = Files.list(destinationDirectory)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".manifest.cargo"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            return loadAllManifests(manifestFiles, privateKey);
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public SortedMap<Integer, BackupIncrementManifest> loadPreviousManifestsForBackup(
            final @NonNull BackupJobConfiguration job) {
        final var historyFolder = job.getDestinationDirectory().resolve(HISTORY_FOLDER);
        if (!Files.exists(historyFolder)) {
            return Collections.emptySortedMap();
        }
        try (var pathStream = Files.list(historyFolder)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(job.getFileNamePrefix()))
                    .filter(path -> path.getFileName().toString().endsWith(MANIFEST_JSON_GZ))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            final var manifests = loadManifests(manifestFiles, null, Long.MAX_VALUE);
            verifyThatAllIncrementsAreFound(manifests);
            if (!manifests.isEmpty() && !job.equals(manifests.get(manifests.lastKey()).getConfiguration())) {
                log.warn("The provided job configuration changed since the last backup. Falling back to FULL backup.");
                manifests.clear();
            }
            return manifests;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public RestoreManifest mergeForRestore(
            final @NonNull SortedMap<Integer, BackupIncrementManifest> manifests) {
        final var maximumAppVersion = findMaximumAppVersion(manifests);
        final var lastIncrementManifest = manifests.get(manifests.lastKey());
        final var maximumTimeStamp = lastIncrementManifest.getStartTimeUtcEpochSeconds();
        final var keys = mergeEncryptionKeys(manifests);
        final var versions = new TreeSet<>(manifests.keySet());
        final var fileNamePrefixes = findAllFilenamePrefixes(manifests);
        final Map<String, Map<UUID, FileMetadata>> files = new HashMap<>();
        final Map<String, Map<UUID, ArchivedFileMetadata>> archivedEntries = new HashMap<>();
        addDirectoriesToFiles(lastIncrementManifest, files);
        final var filesToBeRestored = calculateRemainingFilesAndLinks(lastIncrementManifest);
        populateFilesAndArchiveEntries(manifests, filesToBeRestored, files, archivedEntries);
        files.forEach((key, value) -> {
            LogUtil.logStatistics(value.values(),
                    (type, count) -> log.info("Increment {} contains {} {} items.", key, count, type));
        });
        return RestoreManifest.builder()
                .maximumAppVersion(maximumAppVersion)
                .lastStartTimeUtcEpochSeconds(maximumTimeStamp)
                .versions(versions)
                .configuration(lastIncrementManifest.getConfiguration())
                .fileNamePrefixes(fileNamePrefixes)
                .operatingSystem(OperatingSystem.forOsName(lastIncrementManifest.getOperatingSystem()))
                .files(files)
                .archivedEntries(archivedEntries)
                .encryptionKeys(keys)
                .build();
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
            final @NonNull BackupIncrementManifest manifest) {
        final var fileNamePrefix = manifest.getFileNamePrefix();
        deleteManifestFromHistoryIfExists(backupDirectory, fileNamePrefix);
        deleteManifestAndArchiveFilesFromBackupDirectory(backupDirectory, fileNamePrefix);
    }

    private static Validator createValidator() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            return validatorFactory.getValidator();
        }
    }

    private @NotNull SortedMap<Integer, BackupIncrementManifest> loadManifests(
            final @NotNull List<Path> manifestFiles,
            final @Nullable PrivateKey privateKey,
            final long latestBeforeEpochMillis) {
        final SortedMap<Integer, BackupIncrementManifest> manifests = new TreeMap<>();
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
                manifest.getVersions().forEach(version -> manifests.put(version, manifest));
                if (manifest.getBackupType() == BackupType.FULL) {
                    break;
                }
            } catch (final Exception e) {
                log.warn("Failed to load manifest file: {}", path, e);
            }
            progressTracker.recordProgressInSubSteps(LOAD_MANIFESTS);
        }
        progressTracker.completeStep(LOAD_MANIFESTS);
        return manifests;
    }

    private @NotNull SortedMap<Long, BackupIncrementManifest> loadAllManifests(
            final @NotNull List<Path> manifestFiles,
            final @Nullable PrivateKey privateKey) {
        final SortedMap<Long, BackupIncrementManifest> manifests = new TreeMap<>();
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
                manifests.put(manifest.getStartTimeUtcEpochSeconds(), manifest);
                progressTracker.recordProgressInSubSteps(LOAD_MANIFESTS);
            } catch (final Exception e) {
                log.warn("Failed to load manifest file: {}", path, e);
            }
        }
        if (manifests.isEmpty()) {
            throw new ArchivalException("No manifests found.");
        }
        progressTracker.completeStep(LOAD_MANIFESTS);
        return manifests;
    }

    private void verifyThatAllIncrementsAreFound(final SortedMap<Integer, BackupIncrementManifest> manifests) {
        final var expectedVersions = IntStream.range(0, manifests.size()).boxed().collect(Collectors.toSet());
        if (!expectedVersions.equals(manifests.keySet())) {
            final var notFound = new TreeSet<>(expectedVersions);
            notFound.removeAll(manifests.keySet());
            final var notWanted = new TreeSet<>(manifests.keySet());
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

    private void populateFilesAndArchiveEntries(
            final @NotNull SortedMap<Integer, BackupIncrementManifest> manifests,
            final @NotNull Set<BackupPath> remainingFiles,
            final @NotNull Map<String, Map<UUID, FileMetadata>> files,
            final @NotNull Map<String, Map<UUID, ArchivedFileMetadata>> archivedEntries) {
        //The merged maps should contain all files and archive entries that are relevant for the
        //restore process, because the change detector needs the full information set
        manifests.values().forEach(manifest -> {
            final var relevantFiles = manifest.getFiles().values().stream()
                    .filter(fileMetadata -> remainingFiles.contains(fileMetadata.getAbsolutePath()))
                    .collect(Collectors.toMap(FileMetadata::getId, Function.identity()));
            final var relevantArchiveEntries = manifest.getArchivedEntries().values().stream()
                    .filter(archiveEntry -> archiveEntry.getFiles().stream().anyMatch(relevantFiles::containsKey))
                    .collect(Collectors.toMap(ArchivedFileMetadata::getId, Function.identity()));
            final var fileNamePrefix = manifest.getFileNamePrefix();
            files.computeIfAbsent(fileNamePrefix, prefix -> new ConcurrentHashMap<>())
                    .putAll(relevantFiles);
            archivedEntries.computeIfAbsent(fileNamePrefix, prefix -> new ConcurrentHashMap<>())
                    .putAll(relevantArchiveEntries);
        });
    }

    private @NotNull Set<BackupPath> calculateRemainingFilesAndLinks(
            final @NotNull BackupIncrementManifest lastIncrementManifest) {
        return lastIncrementManifest.getFiles().values().stream()
                .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                .filter(fileMetadata -> fileMetadata.getFileType().isContentSource())
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toSet());
    }

    private void addDirectoriesToFiles(
            final @NotNull BackupIncrementManifest lastIncrementManifest,
            final @NotNull Map<String, Map<UUID, FileMetadata>> files) {
        lastIncrementManifest.getFiles().values().stream()
                .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                .filter(fileMetadata -> !fileMetadata.getFileType().isContentSource())
                .forEach(file -> files.computeIfAbsent(lastIncrementManifest.getFileNamePrefix(), k -> new HashMap<>())
                        .put(file.getId(), file));
    }

    private @NotNull AppVersion findMaximumAppVersion(
            final @NotNull SortedMap<Integer, BackupIncrementManifest> manifests) {
        return manifests.values().stream()
                .map(BackupIncrementManifest::getAppVersion)
                .max(AppVersion::compareTo)
                .orElse(new AppVersion());
    }

    private @NotNull SortedMap<String, SortedSet<Integer>> findAllFilenamePrefixes(
            final @NotNull SortedMap<Integer, BackupIncrementManifest> manifests) {
        final var result = new TreeMap<String, SortedSet<Integer>>();
        manifests.values().forEach(manifest -> result.put(manifest.getFileNamePrefix(), manifest.getVersions()));
        return result;
    }

    private @NotNull SortedMap<Integer, Map<Integer, String>> mergeEncryptionKeys(
            final @NotNull SortedMap<Integer, BackupIncrementManifest> manifests) {
        final var keys = new TreeMap<Integer, Map<Integer, String>>();
        manifests.values().stream()
                .map(BackupIncrementManifest::getEncryptionKeys)
                .filter(k -> k != null && !k.isEmpty())
                .forEach(keys::putAll);
        return keys;
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
