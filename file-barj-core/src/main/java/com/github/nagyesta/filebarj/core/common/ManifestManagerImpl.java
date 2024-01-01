package com.github.nagyesta.filebarj.core.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.model.enums.Change;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Default implementation of {@link ManifestManager}.
 */
@Slf4j
public class ManifestManagerImpl implements ManifestManager {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public BackupIncrementManifest generateManifest(
            @NonNull final BackupJobConfiguration jobConfiguration,
            @NonNull final BackupType backupTypeOverride,
            final int nextVersion) {
        final var startTimeEpochSecond = Instant.now().getEpochSecond();
        final var fileNamePrefix = jobConfiguration.getFileNamePrefix() + "-" + startTimeEpochSecond;
        final var manifest = BackupIncrementManifest.builder()
                .appVersion(new AppVersion())
                .configuration(jobConfiguration)
                .backupType(backupTypeOverride)
                .versions(new TreeSet<>(Set.of(nextVersion)))
                .files(new HashMap<>())
                .archivedEntries(new HashMap<>())
                .startTimeUtcEpochSeconds(startTimeEpochSecond)
                .fileNamePrefix(fileNamePrefix)
                .build();
        Optional.ofNullable(jobConfiguration.getEncryptionKey())
                .ifPresent(manifest::generateDataEncryptionKeys);
        return manifest;
    }

    @Override
    public void persist(
            @NonNull final BackupIncrementManifest manifest) {
        validate(manifest, ValidationRules.Persisted.class);
        final var backupDestination = manifest.getConfiguration().getDestinationDirectory().toFile();
        final var backupHistoryDir = new File(backupDestination, ".history");
        //noinspection ResultOfMethodCallIgnored
        backupHistoryDir.mkdirs();
        final var plainManifestFile = new File(backupHistoryDir, manifest.getFileNamePrefix() + ".manifest.json.gz");
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
            @NonNull final Path destinationDirectory,
            @NonNull final String fileNamePrefix,
            @Nullable final PrivateKey privateKey,
            final long latestBeforeEpochMillis) {
        try (var pathStream = Files.list(destinationDirectory)) {
            final var manifestFiles = pathStream
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".manifest.cargo"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            final SortedMap<Integer, BackupIncrementManifest> manifests = new TreeMap<>();
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
                    //ignore for now, the set of manifests will be verified later
                    log.debug("Failed to load manifest file: {}", path, e);
                }
            }
            if (manifests.keySet().isEmpty()) {
                throw new ArchivalException("No manifests found.");
            }
            final var expectedVersions = IntStream.range(0, manifests.size()).boxed().collect(Collectors.toSet());
            if (!expectedVersions.equals(manifests.keySet())) {
                final var notFound = new TreeSet<>(expectedVersions);
                notFound.removeAll(manifests.keySet());
                final var notWanted = new TreeSet<>(manifests.keySet());
                notWanted.removeAll(expectedVersions);
                if (!notFound.isEmpty()) {
                    log.error("Expected manifest version but not found: " + notFound);
                }
                if (!notWanted.isEmpty()) {
                    log.error("Found manifest version but not expected: " + notWanted);
                }
                throw new ArchivalException("The manifest versions do not match the expected versions.");
            }
            return manifests;
        } catch (final IOException e) {
            throw new ArchivalException("Failed to load manifest files.", e);
        }
    }

    @Override
    public RestoreManifest mergeForRestore(
            @NonNull final SortedMap<Integer, BackupIncrementManifest> manifests) {
        final var maximumAppVersion = findMaximumAppVersion(manifests);
        final var lastIncrementManifest = manifests.get(manifests.lastKey());
        final var maximumTimeStamp = lastIncrementManifest.getStartTimeUtcEpochSeconds();
        final var keys = mergeEncryptionKeys(manifests);
        final var versions = new TreeSet<>(manifests.keySet());
        final var fileNamePrefixes = findAllFilenamePrefixes(manifests);
        final Map<String, Map<UUID, FileMetadata>> files = new HashMap<>();
        final Map<String, Map<UUID, ArchivedFileMetadata>> archivedEntries = new HashMap<>();
        addDirectoriesToFiles(lastIncrementManifest, files);
        final var remainingFiles = calculateRemainingFilesAndLinks(lastIncrementManifest);
        populateFilesAndArchiveEntries(manifests, remainingFiles, files, archivedEntries);
        return RestoreManifest.builder()
                .maximumAppVersion(maximumAppVersion)
                .lastStartTimeUtcEpochSeconds(maximumTimeStamp)
                .versions(versions)
                .configuration(lastIncrementManifest.getConfiguration())
                .fileNamePrefixes(fileNamePrefixes)
                .files(files)
                .archivedEntries(archivedEntries)
                .encryptionKeys(keys)
                .build();
    }

    @SuppressWarnings("checkstyle:TodoComment")
    @Override
    public void validate(
            @NonNull final BackupIncrementManifest manifest,
            @NonNull final Class<? extends ValidationRules> forAction) {
        //TODO: implement validation
    }

    private void populateFilesAndArchiveEntries(
            final @NotNull SortedMap<Integer, BackupIncrementManifest> manifests,
            final @NotNull Map<FileMetadata, ArchiveEntryLocator> remainingFiles,
            final @NotNull Map<String, Map<UUID, FileMetadata>> files,
            final @NotNull Map<String, Map<UUID, ArchivedFileMetadata>> archivedEntries) {
        //use reverse order to preserve the latest changes in the files
        manifests.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .map(manifests::get)
                .forEachOrdered(manifest -> {
                    final Set<FileMetadata> toRemove = new HashSet<>();
                    remainingFiles.entrySet().stream()
                            .filter(entry -> manifest.getVersions().contains(entry.getValue().getBackupIncrement()))
                            .forEach(entry -> {
                                //use the file metadata from the last manifest as that is the source of truth
                                files.computeIfAbsent(manifest.getFileNamePrefix(), prefix -> new HashMap<>())
                                        .put(entry.getKey().getId(), entry.getKey());
                                //find the archived entry in the earlier manifests to be able to add the file name prefix
                                final var archivedEntryId = entry.getValue().getEntryName();
                                archivedEntries.computeIfAbsent(manifest.getFileNamePrefix(), prefix -> new HashMap<>())
                                        .put(archivedEntryId, manifest.getArchivedEntries().get(archivedEntryId));
                                toRemove.add(entry.getKey());
                            });
                    toRemove.forEach(remainingFiles::remove);
                });
    }

    @NotNull
    private Map<FileMetadata, ArchiveEntryLocator> calculateRemainingFilesAndLinks(
            @NotNull final BackupIncrementManifest lastIncrementManifest) {
        final Map<FileMetadata, ArchiveEntryLocator> remainingFiles = new HashMap<>();
        lastIncrementManifest.getFiles().values().stream()
                .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                .filter(fileMetadata -> fileMetadata.getFileType().isContentSource())
                .forEach(file -> {
                    remainingFiles.put(file, lastIncrementManifest.getArchivedEntries()
                            .get(file.getArchiveMetadataId())
                            .getArchiveLocation());
                });
        return remainingFiles;
    }

    private void addDirectoriesToFiles(
            @NotNull final BackupIncrementManifest lastIncrementManifest,
            @NotNull final Map<String, Map<UUID, FileMetadata>> files) {
        lastIncrementManifest.getFiles().values().stream()
                .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                .filter(fileMetadata -> !fileMetadata.getFileType().isContentSource())
                .forEach(file -> files.computeIfAbsent(lastIncrementManifest.getFileNamePrefix(), k -> new HashMap<>())
                        .put(file.getId(), file));
    }

    @NotNull
    private AppVersion findMaximumAppVersion(
            @NotNull final SortedMap<Integer, BackupIncrementManifest> manifests) {
        return manifests.values().stream()
                .map(BackupIncrementManifest::getAppVersion)
                .max(AppVersion::compareTo)
                .orElse(new AppVersion());
    }

    @NotNull
    private TreeSet<String> findAllFilenamePrefixes(
            @NotNull final SortedMap<Integer, BackupIncrementManifest> manifests) {
        return manifests.values().stream()
                .map(BackupIncrementManifest::getFileNamePrefix)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @NotNull
    private SortedMap<Integer, Map<Integer, String>> mergeEncryptionKeys(
            @NotNull final SortedMap<Integer, BackupIncrementManifest> manifests) {
        final var keys = new TreeMap<Integer, Map<Integer, String>>();
        manifests.values().stream()
                .map(BackupIncrementManifest::getEncryptionKeys)
                .filter(k -> k != null && !k.isEmpty())
                .forEach(keys::putAll);
        return keys;
    }
}
