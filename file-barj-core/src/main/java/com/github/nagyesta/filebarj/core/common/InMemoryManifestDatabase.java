package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.*;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.util.LogUtil;
import lombok.NonNull;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the ManifestDatabase.
 * Keeps all manifests in the memory entirely just like the legacy implementations.
 */
@SuppressWarnings("checkstyle:TodoComment")
public class InMemoryManifestDatabase implements ManifestDatabase {
    //TODO: implement tests for this class
    private final SortedMap<ImmutableManifestId, BackupIncrementManifest> manifestsById;
    private final SortedMap<String, ImmutableManifestId> manifestIdsByFilenamePrefixes;
    private final ReentrantReadWriteLock manifestLock;
    private final ReentrantLock indexLock;
    private final SortedMap<ManifestId, Map<Long, List<FileMetadata>>> contentSizeIndex;
    private final SortedMap<ManifestId, Map<String, List<FileMetadata>>> contentHashIndex;
    private final Map<String, FileMetadata> nameIndex;
    private boolean indexed;

    public InMemoryManifestDatabase() {
        this.manifestsById = new TreeMap<>();
        this.manifestIdsByFilenamePrefixes = new TreeMap<>();
        this.manifestLock = new ReentrantReadWriteLock();
        this.indexLock = new ReentrantLock();
        this.contentSizeIndex = new TreeMap<>();
        this.contentHashIndex = new TreeMap<>();
        this.nameIndex = new TreeMap<>();
        this.indexed = true;
    }

    @Override
    public ManifestId persistIncrement(@NonNull final BackupIncrementManifest manifest) {
        this.manifestLock.writeLock().lock();
        try {
            final var id = ImmutableManifestId.of(manifest);
            if (manifestsById.containsKey(id)) {
                //listing all index metadata won't work like this
                throw new IllegalStateException("A manifest is already stored for the id: " + id);
            }
            this.manifestsById.put(id, manifest);
            this.manifestIdsByFilenamePrefixes.put(manifest.getFileNamePrefix(), id);
            return id;
        } finally {
            this.indexed = false;
            this.manifestLock.writeLock().unlock();
        }
    }

    @Override
    public void persistFileMetadata(@NonNull final ManifestId manifestId, @NonNull final FileMetadata metadata) {
        this.manifestLock.writeLock().lock();
        try {
            final var id = ImmutableManifestId.of(manifestId);
            if (!manifestsById.containsKey(id)) {
                throw new IllegalStateException("A manifest is not found by id: " + id);
            }
            this.manifestsById.get(id)
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
            return manifestsById.get(manifestId)
                    .getFiles()
                    .get(fileId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public FileMetadata retrieveFileMetadata(@NonNull final String filenamePrefix, @NonNull final UUID fileId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByFilenamePrefix(filenamePrefix);
            return manifestsById.get(manifestId)
                    .getFiles()
                    .get(fileId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public ArchivedFileMetadata retrieveArchiveMetadata(final int increment, @NonNull final UUID archiveId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByIncrement(increment);
            return manifestsById.get(manifestId)
                    .getArchivedEntries()
                    .get(archiveId);
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
            return manifestsById.get(manifestId)
                    .getArchivedEntries()
                    .get(archiveId);
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public Set<FileMetadata> retrieveFileMetadataForArchive(final int increment, @NonNull final UUID archiveId) {
        this.manifestLock.readLock().lock();
        try {
            final var manifestId = manifestIdByIncrement(increment);
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
    public Optional<ArchivedFileMetadata> retrieveLatestArchiveMetadataByFileMetadataId(@NonNull final UUID fileId) {
        this.manifestLock.readLock().lock();
        try {
            return manifestsById.keySet().stream().sorted(Comparator.reverseOrder())
                    .map(manifestsById::get)
                    .filter(manifest -> manifest.getFiles().containsKey(fileId))
                    .findFirst()
                    .map(manifest -> {
                        final var archiveMetadataId = manifest.getFiles().get(fileId).getArchiveMetadataId();
                        return manifest.getArchivedEntries().get(archiveMetadataId);
                    });
        } finally {
            this.manifestLock.readLock().unlock();
        }
    }

    @Override
    public FileMetadata retrieveLatestFileMetadataBySourcePath(@NonNull final BackupPath sourcePath) {
        this.manifestLock.readLock().lock();
        try {
            index();
            return nameIndex.get(sourcePath.toString());
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
            return manifestsById.get(manifestsById.lastKey()).getConfiguration();
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
    public SecretKey getDataEncryptionKey(@NonNull final ArchiveEntryLocator archiveLocation) {
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
    public SecretKey getLatestDataIndexEncryptionKey() {
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
                indexByContent(this.contentHashIndex, FileMetadata::getOriginalHash);
            }
            final Map<String, FileMetadata> nameIndexMap = new TreeMap<>();
            //populate files in reverse manifest order to ensure each file has the latest metadata saved
            manifestsById.keySet().stream()
                    .sorted(Comparator.reverseOrder())
                    .map(manifestsById::get)
                    .map(BackupIncrementManifest::getFiles)
                    .forEachOrdered(files -> files.entrySet().stream()
                            .filter(entry -> entry.getValue().getStatus() != Change.DELETED)
                            //put the file only if it is not already in the index
                            .forEach(entry -> nameIndexMap.putIfAbsent(entry.getValue().getAbsolutePath().toString(), entry.getValue())));
            this.nameIndex.clear();
            this.nameIndex.putAll(nameIndexMap);
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
}
