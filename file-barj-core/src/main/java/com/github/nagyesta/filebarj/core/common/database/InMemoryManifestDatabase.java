package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupToOsMapper;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the ManifestDatabase.
 * Keeps all manifests in the memory entirely just like the legacy implementations.
 */
@Slf4j
@NotNullByDefault
@SuppressWarnings({"checkstyle:TodoComment"})
public class InMemoryManifestDatabase implements ManifestDatabase {
    //TODO: implement tests for this class
    private final ConcurrentSortedMap<ManifestId, BackupIncrementManifest> manifestsById;
    private final ConcurrentSortedMap<ManifestId, SortedSet<ManifestId>> referencedManifests;
    private final ConcurrentSortedMap<ManifestId, Map<Long, List<FileMetadata>>> contentSizeIndex;
    private final ConcurrentSortedMap<ManifestId, Map<String, List<FileMetadata>>> contentHashIndex;
    private final ConcurrentSortedMap<BackupPath, FileMetadata> nameIndex;
    private final ConcurrentSortedMap<ManifestId, Set<BackupPath>> pathIndex;
    private final ConcurrentSortedMap<UUID, Map<BackupPath, Path>> fileSets;
    private final ConcurrentSortedMap<UUID, Map<BackupPath, FileMetadata>> fileMetadataSets;
    private final ConcurrentSortedMap<UUID, Map<BackupPath, Change>> changeSets;
    private final ConcurrentSortedMap<UUID, Map<ArchiveEntryLocator, Set<UUID>>> archiveSets;

    public InMemoryManifestDatabase() {
        this.manifestsById = new ConcurrentSortedMap<>("Id", "Manifest");
        this.referencedManifests = new ConcurrentSortedMap<>("Id", "ReferencedManifestIds");
        this.contentSizeIndex = new ConcurrentSortedMap<>("ContentSize", "Index");
        this.contentHashIndex = new ConcurrentSortedMap<>("ContentHash", "Index");
        this.nameIndex = new ConcurrentSortedMap<>("BackupPath", "FileMetadata");
        this.pathIndex = new ConcurrentSortedMap<>("Id", "Paths");
        this.fileSets = new ConcurrentSortedMap<>("UUID", "FileSet");
        this.fileMetadataSets = new ConcurrentSortedMap<>("UUID", "FileMetadataSet");
        this.changeSets = new ConcurrentSortedMap<>("UUID", "ChangeSet");
        this.archiveSets = new ConcurrentSortedMap<>("UUID", "ArchiveSet");
    }

    @Override
    public SortedSet<ManifestId> getAllManifestIds() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(manifestsById.keySet()));
    }

    @Override
    public ManifestId persistIncrement(@NonNull final BackupIncrementManifest manifest) {
        final var id = ImmutableManifestId.of(manifest);
        if (manifest.getAppVersion().compareTo(new AppVersion()) > 0) {
            throw new IllegalArgumentException("Manifest was saved with a newer version of the application: " + id);
        }
        if (manifestsById.containsKey(id)) {
            //listing all index metadata won't work like this
            throw new IllegalStateException("A manifest is already stored for the id: " + id);
        }
        final var clone = BackupIncrementManifest.copyOfBaseProperties(manifest);
        this.manifestsById.put(id, clone);
        return id;
    }

    @Override
    public ManifestId createMergedIncrement(@NonNull final SortedSet<ManifestId> manifestsToMerge) {
        index();
        final var manifestId = persistIncrement(BackupIncrementManifest.mergeBaseProperties(manifestsToMerge.stream()
                .map(manifestId1 -> this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId1)))
                .collect(Collectors.toCollection(TreeSet::new))));
        this.referencedManifests.put(ImmutableManifestId.of(manifestId), manifestsToMerge.stream()
                .map(ImmutableManifestId::of)
                .collect(Collectors.toCollection(TreeSet::new)));
        final var source = this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestsToMerge.last()));
        final var target = this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
        target.getFiles().putAll(source.getFiles());
        target.getArchivedEntries().putAll(source.getArchivedEntries());
        return manifestId;
    }

    @Override
    public void persistFileMetadata(@NonNull final ManifestId manifestId, @NonNull final FileMetadata metadata) {
        final var id = ImmutableManifestId.of(manifestId);
        this.manifestsById.getExistingValue(ImmutableManifestId.of(id))
                .getFiles()
                .put(metadata.getId(), metadata);
    }

    @Override
    public void persistArchiveMetadata(@NonNull final ManifestId manifestId, @NonNull final ArchivedFileMetadata metadata) {
        final var manifest = this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
        manifest.getArchivedEntries()
                .put(metadata.getId(), metadata);
        metadata.getFiles().forEach(fileId -> {
            final var fileMetadata = manifest.getFiles().get(fileId);
            if (fileMetadata == null) {
                throw new IllegalStateException("A manifest does not contain the referenced file: " + fileId);
            }
            fileMetadata.setArchiveMetadataId(metadata.getId());
        });
    }

    @Override
    public ArchivedFileMetadata retrieveArchiveMetadata(final ArchiveEntryLocator archiveEntryLocator) {
        final var manifestId = manifestIdByIncrement(archiveEntryLocator.getBackupIncrement());
        return findExistingArchiveEntryMetadata(manifestId, archiveEntryLocator.getEntryName());
    }

    @Override
    public @Nullable ArchivedFileMetadata retrieveLatestArchiveMetadataByFileMetadataId(@NonNull final UUID fileId) {
        return manifestsById.keySet().stream().sorted(Comparator.reverseOrder())
                .map(manifestsById::get)
                .filter(manifest -> manifest.getFiles().containsKey(fileId))
                .findFirst()
                .map(manifest -> {
                    final var archiveMetadataId = manifest.getFiles().get(fileId).getArchiveMetadataId();
                    return manifest.getArchivedEntries().get(archiveMetadataId);
                })
                .orElse(null);
    }

    @Override
    public @Nullable FileMetadata retrieveLatestFileMetadataBySourcePath(@NonNull final BackupPath sourcePath) {
        index();
        return nameIndex.get(sourcePath);
    }

    @Override
    public SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalSizeBytes(@NonNull final Long originalSizeBytes) {
        index();
        return findByOriginalValue(contentSizeIndex, originalSizeBytes);
    }

    @Override
    public SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalHash(@NonNull final String originalHash) {
        index();
        return findByOriginalValue(contentHashIndex, originalHash);
    }

    @Override
    public boolean existsInLastIncrement(@NonNull final FileMetadata fileMetadata) {
        return manifestsById.get(manifestsById.lastKey()).getFiles().containsKey(fileMetadata.getId());
    }

    @Override
    public boolean isEmpty() {
        return manifestsById.isEmpty()
                || manifestsById.values().stream().map(BackupIncrementManifest::getFiles).allMatch(Map::isEmpty);
    }

    @Override
    public int nextIncrement() {
        if (manifestsById.isEmpty()) {
            return 0;
        }
        return manifestsById.lastKey().getMaxVersion() + 1;
    }

    @Override
    public int size() {
        return manifestsById.size();
    }

    @Override
    public BackupJobConfiguration getLatestConfiguration() {
        if (manifestsById.isEmpty()) {
            throw new IllegalStateException("No manifests found.");
        }
        return getConfiguration(manifestsById.lastKey());
    }

    @Override
    public BackupJobConfiguration getConfiguration(final @NonNull ManifestId manifestId) {
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).getConfiguration();
    }

    @Override
    public SortedMap<FileType, Long> getFileStatistics(@NonNull final ManifestId manifestId) {
        return LogUtil.countsByType(this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).getFiles().values());
    }

    @Override
    public void setDataFileNames(@NonNull final ManifestId manifestId, @NonNull final List<String> dataFiles) {
        this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).setDataFileNames(dataFiles);
    }

    @Override
    public void setIndexFileName(@NonNull final ManifestId manifestId, @NonNull final String indexFile) {
        this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).setIndexFileName(indexFile);
    }

    @Deprecated
    @Override
    public BackupIncrementManifest get(@NonNull final ManifestId manifestId) {
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
    }

    @Override
    public SortedSet<Integer> getAllVersionIncrements() {
        return manifestsById.keySet().stream()
                .map(ManifestId::getVersions)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public @Nullable SecretKey getDataEncryptionKey(@NonNull final ArchiveEntryLocator archiveLocation) {
        final var increment = archiveLocation.getBackupIncrement();
        final var id = manifestIdByIncrement(increment);
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(id)).dataEncryptionKey(archiveLocation);
    }

    @Override
    public String getLatestFileNamePrefix() {
        if (manifestsById.isEmpty()) {
            throw new IllegalStateException("No manifests found.");
        }
        return manifestsById.get(manifestsById.lastKey()).getFileNamePrefix();
    }

    @Override
    public @Nullable SecretKey getLatestDataIndexEncryptionKey() {
        if (manifestsById.isEmpty()) {
            throw new IllegalStateException("No manifests found.");
        }
        return manifestsById.get(manifestsById.lastKey()).dataIndexEncryptionKey();
    }

    @Override
    public SecretKey getDataIndexDecryptionKey(
            @NonNull final PrivateKey kek,
            @NonNull final ManifestId manifestId) {
        if (manifestsById.isEmpty()) {
            throw new IllegalStateException("No manifests found.");
        }
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).dataIndexDecryptionKey(kek, manifestId.getMinVersion());
    }

    @Override
    public SecretKey getDataDecryptionKey(
            @NonNull final PrivateKey kek,
            @NonNull final ArchiveEntryLocator entryName,
            @NonNull final ManifestId manifestId) {
        if (manifestsById.isEmpty()) {
            throw new IllegalStateException("No manifests found.");
        }
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).dataDecryptionKey(kek, entryName);
    }

    @Override
    public String getFileNamePrefix(@NonNull final ManifestId manifestId) {
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).getFileNamePrefix();
    }

    @Override
    public long totalCountOfArchiveEntries(@NonNull final ManifestId manifestId) {
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).getArchivedEntries().size();
    }

    @Override
    public Set<String> retrieveArchiveEntityPathsFor(
            @NonNull final ManifestId merged,
            @NonNull final ManifestId storageSource) {
        if (!referencedManifests.containsKey(merged) || !referencedManifests.get(merged).contains(storageSource)) {
            throw new IllegalArgumentException("Cannot find reference between " + merged + " and " + storageSource);
        }
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(merged)).getArchivedEntries().values().stream()
                .map(ArchivedFileMetadata::getArchiveLocation)
                .filter(archiveLocation -> storageSource.getVersions().contains(archiveLocation.getBackupIncrement()))
                .map(ArchiveEntryLocator::asEntryPath)
                .collect(Collectors.toSet());
    }

    @Override
    public FileSetId retrieveFilesFilteredBy(
            @NonNull final ManifestId manifestId,
            @NonNull final BackupPath includedPath,
            @NonNull final Collection<FileType> allowedTypes) {
        final var filterResult = this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).getFiles().values().stream()
                .filter(fileMetadata -> allowedTypes.contains(fileMetadata.getFileType()))
                .filter(fileMetadata -> fileMetadata.getAbsolutePath().equals(includedPath)
                        || fileMetadata.getAbsolutePath().startsWith(includedPath))
                .toList();
        final var fileSetId = this.createFileSet();
        persistFileSetItems(fileSetId, filterResult.stream().map(FileMetadata::getAbsolutePath).toList(), BackupPath::toOsPath);
        persistParsedFileMetadataItemsForFileSet(fileSetId, filterResult);
        return fileSetId;
    }

    @Override
    public long originalSizeOfFilesFilteredBy(
            @NonNull final ManifestId manifestId,
            @NonNull final BackupPath includedPath) {
        try (var fileSetId = retrieveFilesFilteredBy(manifestId, includedPath, FileType.allTypes())) {
            return fileMetadataSets.getExistingValue(fileSetId.id())
                    .values().stream()
                    .map(FileMetadata::getOriginalSizeBytes)
                    .reduce(0L, Long::sum);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Map<BackupPath, String> retrieveFileErrors(@NonNull final ManifestId manifestId) {
        return this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId)).getFiles().values().stream()
                .filter(fileMetadata -> fileMetadata.getError() != null)
                .collect(Collectors.toMap(FileMetadata::getAbsolutePath, FileMetadata::getError));
    }

    @Deprecated
    @Override
    public Set<BackupPath> retrieveAllPaths(final ManifestId manifestId) {
        //verify that the mainfest exists
        this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
        return Collections.unmodifiableSet(pathIndex.get(ImmutableManifestId.of(manifestId)));
    }

    @Override
    public boolean backupContainsPath(
            @NonNull final ManifestId manifestId,
            @NonNull final String linkTargetPath) {
        this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
        return pathIndex.get(ImmutableManifestId.of(manifestId)).stream()
                .anyMatch(backupPath -> backupPath.toString().equals(linkTargetPath));
    }

    @Override
    public boolean backupContainsPath(
            @NonNull final ManifestId manifestId,
            @NonNull final Path osPath,
            @NonNull final BackupToOsMapper backupToOsMapper) {
        this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
        return pathIndex.get(ImmutableManifestId.of(manifestId)).stream()
                .map(backupToOsMapper::mapToOsPath)
                .anyMatch(path -> path.equals(osPath));
    }

    @Override
    public FileSetId createFileSet() {
        final var fileSetId = FileSetId.of(this);
        fileSets.put(fileSetId.id(), new ConcurrentHashMap<>());
        fileMetadataSets.put(fileSetId.id(), new ConcurrentHashMap<>());
        return fileSetId;
    }

    @Override
    public void persistFileSetItems(
            @NonNull final FileSetId fileSetId,
            @NonNull final Collection<Path> paths) {
        final var fileSet = fileSets.getExistingValue(fileSetId.id());
        paths.forEach(path -> fileSet.put(BackupPath.of(path), path));
    }

    @Override
    public void persistFileSetItems(
            @NonNull final FileSetId fileSetId,
            @NonNull final Collection<BackupPath> paths,
            @NonNull final BackupToOsMapper mapper) {
        final var fileSet = fileSets.getExistingValue(fileSetId.id());
        paths.forEach(path -> fileSet.put(path, mapper.mapToOsPath(path)));
    }

    @Override
    public void persistParsedFileMetadataItemsForFileSet(
            @NonNull final FileSetId fileSetId,
            @NonNull final Collection<FileMetadata> fileMetadata) {
        final var fileMetadataSet = fileMetadataSets.getExistingValue(fileSetId.id());
        fileMetadata.forEach(f -> fileMetadataSet.put(f.getAbsolutePath(), f));
    }

    @Override
    public boolean isFileSetEmpty(@NonNull final FileSetId fileSetId) {
        return fileSets.getExistingValue(fileSetId.id()).isEmpty();
    }

    @Override
    public long countFileSetItems(@NonNull final FileSetId fileSetId) {
        return fileSets.getExistingValue(fileSetId.id()).size();
    }

    @Override
    public long sumContentSize(
            @NonNull final ManifestId manifestId,
            @NonNull final FileSetId fileSetId) {
        final var filePaths = fileSets.getExistingValue(fileSetId.id()).keySet();
        return manifestsById.getExistingValue(ImmutableManifestId.of(manifestId))
                .getFiles().values().stream()
                .filter(fileMetadata -> filePaths.contains(fileMetadata.getAbsolutePath()))
                .map(FileMetadata::getOriginalSizeBytes)
                .reduce(0L, Long::sum);
    }

    @Override
    public long sumContentSize(
            @NonNull final FileSetId fileSetId) {
        final var fileMetadataSet = fileMetadataSets.getExistingValue(fileSetId.id()).values();
        return fileMetadataSet.stream()
                .map(FileMetadata::getOriginalSizeBytes)
                .reduce(0L, Long::sum);
    }

    @Override
    public List<FileMetadata> getNextPageOfFileMetadataSetItemsOrderByPath(
            @NonNull final FileSetId fileSetId,
            final int offset,
            final int pageSize) {
        assertValidOffset(offset);
        assertValidPageSize(pageSize);
        return fileMetadataSets.getExistingValue(fileSetId.id()).values().stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath))
                .skip(offset)
                .limit(pageSize)
                .toList();
    }

    @Override
    public List<FileMetadata> getNextPageOfFileMetadataSetItemsReverseOrderByPath(
            @NonNull final FileSetId fileSetId,
            final int offset,
            final int pageSize) {
        assertValidOffset(offset);
        assertValidPageSize(pageSize);
        return fileMetadataSets.getExistingValue(fileSetId.id()).values().stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath).reversed())
                .skip(offset)
                .limit(pageSize)
                .toList();
    }

    @Override
    public List<FileMetadata> getNextPageOfFileMetadataSetItemsOrderByHash(
            @NonNull final FileSetId fileSetId,
            final int offset,
            final int pageSize) {
        assertValidOffset(offset);
        assertValidPageSize(pageSize);
        return fileMetadataSets.getExistingValue(fileSetId.id()).values().stream()
                .sorted(FileMetadata::compareByOriginalHash)
                .skip(offset)
                .limit(pageSize + 1)
                .toList();
    }

    private static void assertValidOffset(final int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
    }

    private static void assertValidPageSize(final int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be greater than 0");
        }
    }

    @Override
    public List<BackupPath> getNextPageOfFileSetItemBackupPaths(
            @NonNull final FileSetId fileSetId,
            final int offset,
            final int pageSize) {
        assertValidOffset(offset);
        assertValidPageSize(pageSize);
        return fileSets.getExistingValue(fileSetId.id())
                .keySet().stream()
                .skip(offset)
                .limit(pageSize)
                .toList();
    }

    @Override
    public List<Path> retrieveFileWithCaseSensitivityIssues(@NonNull final FileSetId fileSetId) {
        return fileSets.getExistingValue(fileSetId.id()).values().stream()
                .collect(Collectors.groupingBy(path -> path.toString().toLowerCase()))
                .values().stream()
                .filter(paths -> paths.size() > 1)
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public void deleteFileSet(@NonNull final FileSetId fileSetId) {
        fileSets.remove(fileSetId.id());
    }

    @Override
    public FileSetId retrieveFilesWithContentChanges(
            @NonNull final ChangeSetId changeSetId,
            @NonNull final FileSetId contentSourcesSetId) {
        final var resultSetId = createFileSet();
        final var resultSet = fileSets.getExistingValue(resultSetId.id());
        final var resultMetadataSet = fileMetadataSets.getExistingValue(resultSetId.id());
        final var contentSources = fileSets.getExistingValue(contentSourcesSetId.id());
        final var changedFiles = changeSets.getExistingValue(changeSetId.id()).entrySet().stream()
                .filter(entry -> contentSources.containsKey(entry.getKey()))
                .filter(entry -> entry.getValue().isRestoreContent())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        fileMetadataSets.getExistingValue(contentSourcesSetId.id()).entrySet().stream()
                .filter(entry -> changedFiles.contains(entry.getKey())
                        //always keep the symbolic links in scope as their change status may not be accurate
                        //when restoring links referencing other files from the backup scope, and we are
                        //restoring the content to a new location instead of the original source directory
                        || entry.getValue().getFileType() == FileType.SYMBOLIC_LINK)
                .forEach(entry -> {
                    resultSet.put(entry.getKey(), contentSources.get(entry.getKey()));
                    resultMetadataSet.put(entry.getKey(), entry.getValue());
                });
        return resultSetId;
    }

    @Override
    public FileSetId retrieveFilesWithMetadataChanges(
            final ChangeSetId changeSetId,
            final FileSetId fileSetId) {
        final var resultSetId = createFileSet();
        final var resultSet = fileSets.getExistingValue(resultSetId.id());
        final var resultMetadataSet = fileMetadataSets.getExistingValue(resultSetId.id());
        final var contentSources = fileSets.getExistingValue(fileSetId.id());
        final var changedFiles = changeSets.getExistingValue(changeSetId.id()).entrySet().stream()
                .filter(entry -> contentSources.containsKey(entry.getKey()))
                .filter(entry -> entry.getValue().isRestoreMetadata())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        fileMetadataSets.getExistingValue(fileSetId.id()).entrySet().stream()
                .filter(entry -> changedFiles.contains(entry.getKey()))
                .forEach(entry -> {
                    resultSet.put(entry.getKey(), contentSources.get(entry.getKey()));
                    resultMetadataSet.put(entry.getKey(), entry.getValue());
                });
        return resultSetId;
    }

    @Override
    public Map<ManifestId, ArchiveSetId> createRelevantRestoreScopeFor(
            @NonNull final ManifestId selectedIncrement,
            @NonNull final FileSetId filesWithContentChanges) {
        final var contentBackupPathsInScope = fileSets.getExistingValue(filesWithContentChanges.id()).keySet();
        final Map<ManifestId, ArchiveSetId> result = new TreeMap<>();
        manifestsById.keySet().stream()
                .filter(incrementId -> incrementId.compareTo(selectedIncrement) <= 0)
                .forEach(incrementId -> {
                    final var archiveEntityPathsForIncrement = retrieveArchiveEntityPathsFor(selectedIncrement, incrementId);
                    final var setId = ArchiveSetId.of(this);
                    final var relevantArchiveEntries = new ConcurrentSortedMap<ArchiveEntryLocator, Set<UUID>>(
                            "ArchiveEntryLocator", "FileMetadataIds");
                    archiveEntityPathsForIncrement.stream()
                            .map(ArchiveEntryLocator::fromEntryPath)
                            .filter(Objects::nonNull)
                            .forEach(locator -> {
                                final var archivedFileMetadata = retrieveArchiveMetadata(locator);
                                final var matchingFileMetadataSet = archivedFileMetadata.getFiles().stream()
                                        .map(manifestsById.get(incrementId).getFiles()::get)
                                        .filter(fileMetadata -> contentBackupPathsInScope.contains(fileMetadata.getAbsolutePath()))
                                        .map(FileMetadata::getId)
                                        .collect(Collectors.toSet());
                                relevantArchiveEntries.put(locator, matchingFileMetadataSet);
                            });
                    archiveSets.put(setId.id(), relevantArchiveEntries);
                    result.put(incrementId, setId);
                });
        return result;
    }

    @Override
    public long countArchiveSetItems(@NonNull final ArchiveSetId archiveSetId) {
        return archiveSets.getExistingValue(archiveSetId.id()).size();
    }

    @Override
    public long countArchiveSetFileItems(@NonNull final ArchiveSetId archiveSetId) {
        return archiveSets.getExistingValue(archiveSetId.id()).values().stream()
                .map(Collection::size)
                .reduce(0, Integer::sum);
    }

    @Override
    public Set<ArchiveEntryLocator> retrieveAllArchiveEntryLocators(@NonNull final ArchiveSetId archiveSetId) {
        return Set.copyOf(archiveSets.getExistingValue(archiveSetId.id()).keySet());
    }

    @Override
    public SortedSet<FileMetadata> retrieveFileMetadataInArchiveSetByLocator(
            @NonNull final ManifestId manifestId,
            @NonNull final ArchiveSetId archiveSetId,
            @NonNull final ArchiveEntryLocator locator) {
        final var files = manifestsById.getExistingValue(manifestId).getFiles();
        return archiveSets.getExistingValue(archiveSetId.id()).get(locator).stream()
                .map(files::get)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public ArchiveSetId saveRestorePartition(
            @NonNull final ManifestId manifestId,
            @NonNull final ArchiveSetId archiveSetId,
            @NonNull final List<ArchiveEntryLocator> chunk) {
        final var resultSetId = ArchiveSetId.of(this);
        final var originalSet = archiveSets.getExistingValue(archiveSetId.id());
        final var relevantArchiveEntries = new ConcurrentSortedMap<ArchiveEntryLocator, Set<UUID>>(
                "ArchiveEntryLocator", "FileMetadataIds");
        chunk.forEach(entryLocator ->
                relevantArchiveEntries.put(entryLocator, originalSet.get(entryLocator)));
        archiveSets.put(resultSetId.id(), relevantArchiveEntries);
        return resultSetId;
    }

    @Override
    public void deleteArchiveSet(@NonNull final ArchiveSetId archiveSetId) {
        archiveSets.remove(archiveSetId.id());
    }

    @Override
    public ChangeSetId createChangeSet() {
        final var changeSetId = ChangeSetId.of(this);
        changeSets.put(changeSetId.id(), new ConcurrentHashMap<>());
        return changeSetId;
    }

    @Override
    public void persistChangeStatuses(final ChangeSetId changeSetId, final Map<BackupPath, Change> changeStatuses) {
        changeSets.getExistingValue(changeSetId.id()).putAll(changeStatuses);
    }

    @Override
    public void deleteChangeSet(@NonNull final ChangeSetId changeSetId) {
        changeSets.remove(changeSetId.id());
    }

    @Override
    public Optional<Change> retrieveChange(
            final ChangeSetId changeSetId,
            final BackupPath fileAbsolutePath) {
        return Optional.ofNullable(changeSets.getExistingValue(changeSetId.id()).get(fileAbsolutePath));
    }

    @Override
    public Map<FileType, Long> getFileMetadataStatsForFileSet(@NonNull final FileSetId fileSetId) {
        final var fileMetadataMap = this.fileMetadataSets.get(fileSetId.id());
        return fileMetadataMap.values().stream()
                .map(FileMetadata::getFileType)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    @Override
    public SortedMap<Change, Long> getChangeStats(@NonNull final ChangeSetId changeSetId) {
        return new TreeMap<>(this.changeSets.getExistingValue(changeSetId.id()).values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
    }

    @Override
    public void clear() {
        manifestsById.clear();
        nameIndex.clear();
        contentHashIndex.clear();
        contentSizeIndex.clear();
    }

    @Override
    public void close() throws Exception {

    }

    private void index() {
        if (this.manifestsById.hasChangesSinceMark()) {
            return;
        }
        this.manifestsById.writeLock();
        try {
            if (this.manifestsById.hasChangesSinceMark()) {
                return;
            }
            if (manifestsById.get(manifestsById.firstKey()).getConfiguration().getHashAlgorithm() == HashAlgorithm.NONE) {
                indexByContent(this.contentSizeIndex, FileMetadata::getOriginalSizeBytes);
            } else {
                //noinspection DataFlowIssue
                indexByContent(this.contentHashIndex, FileMetadata::getOriginalHash);
            }
            final Map<BackupPath, FileMetadata> nameIndexMap = new TreeMap<>();
            //populate files in reverse manifest order to ensure each file has the latest metadata saved
            manifestsById.keySet().stream()
                    .sorted(Comparator.reverseOrder())
                    .map(manifestsById::get)
                    .map(BackupIncrementManifest::getFiles)
                    .forEachOrdered(files -> files.entrySet().stream()
                            .filter(entry -> entry.getValue().getStatus() != Change.DELETED)
                            //put the file only if it is not already in the index
                            .forEach(entry -> nameIndexMap.putIfAbsent(entry.getValue().getAbsolutePath(), entry.getValue())));
            this.nameIndex.clear();
            this.nameIndex.putAll(nameIndexMap);
            //index non-deleted original file paths
            final Map<ManifestId, Set<BackupPath>> pathIndexMap = new TreeMap<>();
            //noinspection CodeBlock2Expr
            manifestsById.forEach((key, value) -> {
                pathIndexMap.put(key, value.getFiles().values().stream()
                        .filter(fileMetadata -> fileMetadata.getStatus() != Change.DELETED)
                        .map(FileMetadata::getAbsolutePath)
                        .collect(Collectors.toSet()));
            });
            this.pathIndex.clear();
            this.pathIndex.putAll(pathIndexMap);
            this.manifestsById.mark();
        } finally {
            this.manifestsById.writeUnlock();
        }
    }

    private <T> void indexByContent(
            final SortedMap<ManifestId, Map<T, List<FileMetadata>>> contentIndexMap,
            final Function<FileMetadata, T> primaryCriteriaFunction) {
        contentIndexMap.clear();
        manifestsById.forEach((id, manifest) -> {
            //skip merged manifests during indexing
            if (referencedManifests.containsKey(id)) {
                return;
            }
            manifest.getFiles().forEach((uuid, metadata) -> contentIndexMap.computeIfAbsent(id, k -> new HashMap<>())
                    .computeIfAbsent(primaryCriteriaFunction.apply(metadata), k -> new ArrayList<>())
                    .add(metadata));
        });
    }

    private <T> SortedMap<ManifestId, List<FileMetadata>> findByOriginalValue(
            final SortedMap<ManifestId, Map<T, List<FileMetadata>>> indexMap,
            final T originalValue) {
        final SortedMap<ManifestId, List<FileMetadata>> result = new TreeMap<>();
        for (final var increment : indexMap.keySet()) {
            final var index = indexMap.get(increment);
            if (index.containsKey(originalValue)) {
                result.put(increment, index.get(originalValue));
            }
        }
        return result;
    }

    private ImmutableManifestId manifestIdByIncrement(final int increment) {
        return manifestsById.keySet().stream()
                .filter(key -> key.getVersions().contains(increment))
                .findFirst()
                .map(ImmutableManifestId::of)
                .orElseThrow(() -> new IllegalStateException("No manifest found for increment: " + increment));
    }

    private ArchivedFileMetadata findExistingArchiveEntryMetadata(
            final ImmutableManifestId manifestId,
            final UUID archiveEntryId) {
        final var manifest = this.manifestsById.getExistingValue(ImmutableManifestId.of(manifestId));
        final var map = manifest.getArchivedEntries();
        if (!map.containsKey(archiveEntryId)) {
            throw new IllegalStateException("The manifest does not contain the referenced archive entry: " + archiveEntryId);
        }
        return map.get(archiveEntryId);
    }

}
