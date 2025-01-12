package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.BackupToOsMapper;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import lombok.NonNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the ManifestDatabase.
 * Keeps all manifests in the memory entirely just like the legacy implementations.
 */
@NotNullByDefault
@SuppressWarnings({"checkstyle:TodoComment"})
public class InMemoryManifestDatabase implements ManifestDatabase {
    //TODO: implement tests for this class
    private final SortedMap<ImmutableManifestId, BackupIncrementManifest> manifestsById;
    private final SortedMap<String, ImmutableManifestId> manifestIdsByFilenamePrefixes;
    private final SortedMap<ManifestId, SortedSet<ManifestId>> referencedManifests;
    private final ReentrantReadWriteLock manifestLock;
    private final ReentrantLock indexLock;
    private final SortedMap<ManifestId, Map<Long, List<FileMetadata>>> contentSizeIndex;
    private final SortedMap<ManifestId, Map<String, List<FileMetadata>>> contentHashIndex;
    private final Map<BackupPath, FileMetadata> nameIndex;
    private final Map<ManifestId, Set<BackupPath>> pathIndex;
    private boolean indexed;

    public InMemoryManifestDatabase() {
        this.manifestsById = new TreeMap<>();
        this.manifestIdsByFilenamePrefixes = new TreeMap<>();
        this.referencedManifests = new TreeMap<>();
        this.manifestLock = new ReentrantReadWriteLock();
        this.indexLock = new ReentrantLock();
        this.contentSizeIndex = new TreeMap<>();
        this.contentHashIndex = new TreeMap<>();
        this.nameIndex = new TreeMap<>();
        this.pathIndex = new TreeMap<>();
        this.indexed = true;
    }

    @Override
    public SortedSet<ManifestId> getAllManifestIds() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(manifestsById.keySet()));
    }

    @Override
    public ManifestId persistIncrement(@NonNull final BackupIncrementManifest manifest) {
        this.manifestLock.writeLock().lock();
        try {
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
            this.manifestIdsByFilenamePrefixes.put(manifest.getFileNamePrefix(), id);
            return id;
        } finally {
            this.indexed = false;
            this.manifestLock.writeLock().unlock();
        }
    }

    @Override
    public ManifestId createMergedIncrement(@NonNull final SortedSet<ManifestId> manifestsToMerge) {
        this.manifestLock.writeLock().lock();
        try {
            index();
            final var manifestId = persistIncrement(BackupIncrementManifest.mergeBaseProperties(manifestsToMerge.stream()
                    .map(this::findExistingManifest)
                    .collect(Collectors.toCollection(TreeSet::new))));
            this.referencedManifests.put(ImmutableManifestId.of(manifestId), manifestsToMerge.stream()
                    .map(ImmutableManifestId::of)
                    .collect(Collectors.toCollection(TreeSet::new)));
            final var source = this.findExistingManifest(manifestsToMerge.last());
            final var target = this.findExistingManifest(manifestId);
            target.getFiles().putAll(source.getFiles());
            target.getArchivedEntries().putAll(source.getArchivedEntries());
            return manifestId;
        } finally {
            //the index is still good because there was no data transfer
            this.manifestLock.writeLock().unlock();
        }
    }

    @Override
    public void persistFileMetadata(@NonNull final ManifestId manifestId, @NonNull final FileMetadata metadata) {
        this.manifestLock.writeLock().lock();
        try {
            final var id = ImmutableManifestId.of(manifestId);
            this.findExistingManifest(id)
                    .getFiles()
                    .put(metadata.getId(), metadata);
        } finally {
            this.indexed = false;
            this.manifestLock.writeLock().unlock();
        }
    }

    @Override
    public void persistArchiveMetadata(@NonNull final ManifestId manifestId, @NonNull final ArchivedFileMetadata metadata) {
        this.manifestLock.writeLock().lock();
        try {
            final var manifest = findExistingManifest(manifestId);
            manifest.getArchivedEntries()
                    .put(metadata.getId(), metadata);
            metadata.getFiles().forEach(fileId -> {
                final var fileMetadata = manifest.getFiles().get(fileId);
                if (fileMetadata == null) {
                    throw new IllegalStateException("A manifest does not contain the referenced file: " + fileId);
                }
                fileMetadata.setArchiveMetadataId(metadata.getId());
            });
        } finally {
            this.indexed = false;
            this.manifestLock.writeLock().unlock();
        }
    }

    @Override
    public FileMetadata retrieveFileMetadata(final int increment, @NonNull final UUID fileId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByIncrement(increment);
            return findExistingFileMetadata(manifestId, fileId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public FileMetadata retrieveFileMetadata(@NonNull final String filenamePrefix, @NonNull final UUID fileId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByFilenamePrefix(filenamePrefix);
            return findExistingFileMetadata(manifestId, fileId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public ArchivedFileMetadata retrieveArchiveMetadata(final int increment, @NonNull final UUID archiveId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByIncrement(increment);
            return findExistingArchiveEntryMetadata(manifestId, archiveId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public ArchivedFileMetadata retrieveArchiveMetadata(@NonNull final ArchiveEntryLocator archiveLocation) {
        this.manifestLock.readLock().lock();
        try {
            return retrieveArchiveMetadata(archiveLocation.getBackupIncrement(), archiveLocation.getEntryName());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public ArchivedFileMetadata retrieveArchiveMetadata(@NonNull final String filenamePrefix, @NonNull final UUID archiveId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByFilenamePrefix(filenamePrefix);
            return findExistingArchiveEntryMetadata(manifestId, archiveId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public Set<FileMetadata> retrieveFileMetadataForArchive(final int increment, @NonNull final UUID archiveId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByIncrement(increment);
            return findExistingArchiveEntryMetadata(manifestId, archiveId)
                    .getFiles().stream()
                    .map(fileId -> findExistingFileMetadata(manifestId, fileId))
                    .collect(Collectors.toSet());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public Set<FileMetadata> retrieveFileMetadataForArchive(@NonNull final String filenamePrefix, @NonNull final UUID archiveId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByFilenamePrefix(filenamePrefix);
            final var manifest = manifestsById.get(manifestId);
            return manifest
                    .getArchivedEntries()
                    .get(archiveId)
                    .getFiles().stream()
                    .map(manifest.getFiles()::get)
                    .collect(Collectors.toSet());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public @Nullable ArchivedFileMetadata retrieveLatestArchiveMetadataByFileMetadataId(@NonNull final UUID fileId) {
        this.manifestLock.readLock().lock();
        try {
            return manifestsById.keySet().stream().sorted(Comparator.reverseOrder())
                    .map(manifestsById::get)
                    .filter(manifest -> manifest.getFiles().containsKey(fileId))
                    .findFirst()
                    .map(manifest -> {
                        final var archiveMetadataId = manifest.getFiles().get(fileId).getArchiveMetadataId();
                        return manifest.getArchivedEntries().get(archiveMetadataId);
                    })
                    .orElse(null);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public @Nullable FileMetadata retrieveLatestFileMetadataBySourcePath(@NonNull final BackupPath sourcePath) {
        this.manifestLock.readLock().lock();
        try {
            index();
            return nameIndex.get(sourcePath);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalSizeBytes(@NonNull final Long originalSizeBytes) {
        this.manifestLock.readLock().lock();
        try {
            index();
            return findByOriginalValue(contentSizeIndex, originalSizeBytes);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public SortedMap<ManifestId, List<FileMetadata>> retrieveFileMetadataByOriginalHash(@NonNull final String originalHash) {
        this.manifestLock.readLock().lock();
        try {
            index();
            return findByOriginalValue(contentHashIndex, originalHash);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public boolean existsInLastIncrement(@NonNull final FileMetadata fileMetadata) {
        this.manifestLock.readLock().lock();
        try {
            return manifestsById.get(manifestsById.lastKey()).getFiles().containsKey(fileMetadata.getId());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        this.manifestLock.readLock().lock();
        try {
            return manifestsById.isEmpty()
                    || manifestsById.values().stream().map(BackupIncrementManifest::getFiles).allMatch(Map::isEmpty);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public int nextIncrement() {
        this.manifestLock.readLock().lock();
        try {
            if (manifestsById.isEmpty()) {
                return 0;
            }
            return manifestsById.lastKey().getMaxVersion() + 1;
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        this.manifestLock.readLock().lock();
        try {
            return manifestsById.size();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public BackupJobConfiguration getLatestConfiguration() {
        this.manifestLock.readLock().lock();
        try {
            if (manifestsById.isEmpty()) {
                throw new IllegalStateException("No manifests found.");
            }
            return getConfiguration(manifestsById.lastKey());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public BackupJobConfiguration getConfiguration(final @NonNull ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            return findExistingManifest(manifestId).getConfiguration();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public SortedMap<FileType, Long> getFileStatistics(@NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            return LogUtil.countsByType(findExistingManifest(manifestId).getFiles().values());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public void setDataFileNames(@NonNull final ManifestId manifestId, @NonNull final List<String> dataFiles) {
        this.manifestLock.writeLock().lock();
        try {
            findExistingManifest(manifestId).setDataFileNames(dataFiles);
        } finally {
            this.manifestLock.writeLock().unlock();
        }
    }

    @Override
    public void setIndexFileName(@NonNull final ManifestId manifestId, @NonNull final String indexFile) {
        this.manifestLock.writeLock().lock();
        try {
            findExistingManifest(manifestId).setIndexFileName(indexFile);
        } finally {
            this.manifestLock.writeLock().unlock();
        }
    }

    @Deprecated
    @Override
    public BackupIncrementManifest get(@NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            return findExistingManifest(manifestId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public SortedSet<Integer> getAllVersionIncrements() {
        this.manifestLock.readLock().lock();
        try {
            return manifestsById.keySet().stream()
                    .map(ManifestId::getVersions)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toCollection(TreeSet::new));
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public @Nullable SecretKey getDataEncryptionKey(@NonNull final ArchiveEntryLocator archiveLocation) {
        this.manifestLock.readLock().lock();
        try {
            final var increment = archiveLocation.getBackupIncrement();
            final var id = manifestIdByIncrement(increment);
            return findExistingManifest(id).dataEncryptionKey(archiveLocation);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public String getLatestFileNamePrefix() {
        this.manifestLock.readLock().lock();
        try {
            if (manifestsById.isEmpty()) {
                throw new IllegalStateException("No manifests found.");
            }
            return manifestsById.get(manifestsById.lastKey()).getFileNamePrefix();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public @Nullable SecretKey getLatestDataIndexEncryptionKey() {
        this.manifestLock.readLock().lock();
        try {
            if (manifestsById.isEmpty()) {
                throw new IllegalStateException("No manifests found.");
            }
            return manifestsById.get(manifestsById.lastKey()).dataIndexEncryptionKey();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public SecretKey getDataIndexDecryptionKey(
            @NonNull final PrivateKey kek,
            @NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            if (manifestsById.isEmpty()) {
                throw new IllegalStateException("No manifests found.");
            }
            return findExistingManifest(manifestId).dataIndexDecryptionKey(kek, manifestId.getMinVersion());
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public SecretKey getDataDecryptionKey(
            @NonNull final PrivateKey kek,
            @NonNull final ArchiveEntryLocator entryName,
            @NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            if (manifestsById.isEmpty()) {
                throw new IllegalStateException("No manifests found.");
            }
            return findExistingManifest(manifestId).dataDecryptionKey(kek, entryName);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public String getFileNamePrefix(@NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            return findExistingManifest(manifestId).getFileNamePrefix();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public long totalCountOfArchiveEntries(@NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            return findExistingManifest(manifestId).getArchivedEntries().size();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public Set<String> retrieveArchiveEntityPathsFor(
            @NonNull final ManifestId merged,
            @NonNull final ManifestId storageSource) {
        if (!referencedManifests.containsKey(merged) || !referencedManifests.get(merged).contains(storageSource)) {
            throw new IllegalArgumentException("Cannot find reference between " + merged + " and " + storageSource);
        }
        return findExistingManifest(merged).getArchivedEntries().values().stream()
                .map(ArchivedFileMetadata::getArchiveLocation)
                .filter(archiveLocation -> storageSource.getVersions().contains(archiveLocation.getBackupIncrement()))
                .map(ArchiveEntryLocator::asEntryPath)
                .collect(Collectors.toSet());
    }

    @Override
    public List<FileMetadata> retrieveFilesFilteredBy(
            @NonNull final ManifestId manifestId,
            @NonNull final BackupPath includedPath,
            @NonNull final Collection<FileType> allowedTypes) {
        this.manifestLock.readLock().lock();
        try {
            return findExistingManifest(manifestId).getFiles().values().stream()
                    .filter(fileMetadata -> allowedTypes.contains(fileMetadata.getFileType()))
                    .filter(fileMetadata -> fileMetadata.getAbsolutePath().equals(includedPath)
                            || fileMetadata.getAbsolutePath().startsWith(includedPath))
                    .toList();
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public long originalSizeOfFilesFilteredBy(
            @NonNull final ManifestId manifestId,
            @NonNull final BackupPath includedPath) {
        return retrieveFilesFilteredBy(manifestId, includedPath, FileType.allTypes()).stream()
                .map(FileMetadata::getOriginalSizeBytes)
                .reduce(0L, Long::sum);
    }

    @Override
    public Map<BackupPath, String> retrieveFileErrors(@NonNull final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            return findExistingManifest(manifestId).getFiles().values().stream()
                    .filter(fileMetadata -> fileMetadata.getError() != null)
                    .collect(Collectors.toMap(FileMetadata::getAbsolutePath, FileMetadata::getError));
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public Set<BackupPath> retrieveAllPaths(final ManifestId manifestId) {
        this.manifestLock.readLock().lock();
        try {
            //verify that the mainfest exists
            findExistingManifest(manifestId);
            return Collections.unmodifiableSet(pathIndex.get(ImmutableManifestId.of(manifestId)));
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public UUID detectChanges(final ManifestId comparedToManifestId, final Collection<Path> scope) {
        return null;
    }

    @Override
    public UUID detectChanges(final ManifestId comparedToManifestId, final BackupToOsMapper pathMapper, final boolean ignoreLinks) {
        return null;
    }

    @Override
    public void clear() {
        manifestLock.writeLock().lock();
        indexLock.lock();
        try {
            manifestsById.clear();
            manifestIdsByFilenamePrefixes.clear();
            nameIndex.clear();
            contentHashIndex.clear();
            contentSizeIndex.clear();
            indexed = true;
        } finally {
            this.manifestLock.writeLock().unlock();
            this.indexLock.unlock();
        }
    }

    @Override
    public void close() throws Exception {

    }

    private void index() {
        if (indexed) {
            return;
        }
        this.indexLock.lock();
        try {
            if (indexed) {
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
            this.indexed = true;
        } finally {
            this.indexLock.unlock();
        }
    }

    private BackupIncrementManifest findExistingManifest(final ManifestId manifestId) {
        final var id = ImmutableManifestId.of(manifestId);
        if (!manifestsById.containsKey(id)) {
            throw new IllegalStateException("A manifest is not found by id: " + id);
        }
        return this.manifestsById.get(id);
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
                .orElseThrow(() -> new IllegalStateException("No manifest found for increment: " + increment));
    }

    private ImmutableManifestId manifestIdByFilenamePrefix(final String filenamePrefix) {
        if (!manifestIdsByFilenamePrefixes.containsKey(filenamePrefix)) {
            throw new IllegalStateException("No manifest found for filenamePrefix: " + filenamePrefix);
        }
        return manifestIdsByFilenamePrefixes.get(filenamePrefix);
    }

    private ArchivedFileMetadata findExistingArchiveEntryMetadata(
            final ImmutableManifestId manifestId,
            final UUID archiveEntryId) {
        return findExistingItem(manifestId, archiveEntryId, BackupIncrementManifest::getArchivedEntries,
                "The manifest does not contain the referenced archive entry: ");
    }

    private FileMetadata findExistingFileMetadata(
            final ImmutableManifestId manifestId,
            final UUID fileId) {
        return findExistingItem(manifestId, fileId, BackupIncrementManifest::getFiles,
                "The manifest does not contain the referenced file: ");
    }

    private <T> T findExistingItem(
            final ImmutableManifestId manifestId,
            final UUID itemId,
            final Function<BackupIncrementManifest, Map<UUID, T>> obtainMapFunction,
            final String messageIfMissing) {
        final var manifest = findExistingManifest(manifestId);
        final var map = obtainMapFunction.apply(manifest);
        if (!map.containsKey(itemId)) {
            throw new IllegalStateException(messageIfMissing + itemId);
        }
        return map.get(itemId);
    }
}
