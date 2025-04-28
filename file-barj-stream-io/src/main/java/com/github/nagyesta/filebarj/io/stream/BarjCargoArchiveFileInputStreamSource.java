package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.exception.ArchiveIntegrityException;
import com.github.nagyesta.filebarj.io.stream.internal.CompositeRestoreStream;
import com.github.nagyesta.filebarj.io.stream.internal.FixedRangeInputStream;
import com.github.nagyesta.filebarj.io.stream.internal.MergingFileInputStream;
import com.github.nagyesta.filebarj.io.stream.internal.OptionalDigestOutputStream;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntityIndex;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import com.github.nagyesta.filebarj.io.stream.model.BarjCargoArchiveEntry;
import com.github.nagyesta.filebarj.io.stream.model.RandomAccessBarjCargoArchiveEntry;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.toChunkFileName;
import static com.github.nagyesta.filebarj.io.stream.BarjCargoUtil.toIndexFileName;
import static com.github.nagyesta.filebarj.io.stream.ReadOnlyArchiveIndex.INDEX_VERSION;
import static com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil.newCipherInputStream;
import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingOutputStream.MEBIBYTE;
import static org.apache.commons.io.FilenameUtils.normalizeNoEndSeparator;
import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

/**
 * Provides {@link java.io.FileInputStream} instances reading entries from BaRJ cargo archive files.
 */
@Slf4j
public class BarjCargoArchiveFileInputStreamSource {

    private static final int MAX_NUMBER_OF_EXAMPLES = 10;
    private final IoFunction<InputStream, InputStream> decompressionFunction;
    private final String hashAlgorithm;
    @Getter
    private final List<BarjCargoEntityIndex> entityIndexes;
    private final SortedMap<String, Path> chunkPaths;

    /**
     * Creates a new instance and sets the parameters needed for the BaRJ cargo streaming unpacking
     * operations.
     *
     * @param config The configuration for the BaRJ cargo archive
     * @throws IOException               If we cannot access the folder or read from it.
     * @throws ArchiveIntegrityException If the archive is in an invalid state.
     */
    public BarjCargoArchiveFileInputStreamSource(final @NotNull BarjCargoInputStreamConfiguration config)
            throws IOException, ArchiveIntegrityException {
        final var folderPath = config.getFolder().toAbsolutePath();
        final var indexFile = Path.of(folderPath.toString(), toIndexFileName(config.getPrefix()));
        this.decompressionFunction = config.getCompressionFunction();
        this.hashAlgorithm = config.getHashAlgorithm();
        log.info("Index file: {}", indexFile);
        final var indexProperties = readProperties(config, indexFile);
        log.debug("Read {} index property keys", indexProperties.size());
        this.entityIndexes = parseEntityIndexes(indexProperties);
        log.debug("Found {} entity indexes", entityIndexes.size());
        validateEntityIndexes(entityIndexes);
        this.chunkPaths = generateFilePathMap(indexProperties, config);
        log.debug("Expecting {} chunk paths", chunkPaths.size());
        verifyFilesExistAndHaveExpectedSizes(indexProperties, chunkPaths);
    }

    /**
     * Returns the {@link BarjCargoArchiveEntry} for the given path.
     *
     * @param path the path
     * @return the entry stored in the archive or {@code null} if not found
     */
    public BarjCargoArchiveEntry getEntry(
            final @NonNull String path) {
        return entityIndexes.stream()
                .filter(e -> e.getPath().equals(path))
                .map(index -> new RandomAccessBarjCargoArchiveEntry(this, index))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such entry: " + path));
    }

    /**
     * Returns an iterator to allow sequential access to the entries in the archive.
     *
     * @return the iterator
     * @throws IOException if an I/O error occurs
     */
    public BarjCargoArchiveEntryIterator getIterator() throws IOException {
        return new BarjCargoArchiveEntryIterator(this, entityIndexes);
    }

    /**
     * Returns an iterator to allow sequential access to the entries in the archive. The iterator
     * tries to skip the beginning and end of the archive holding no entries from the ones in scope.
     *
     * @param archiveEntriesInScope the entries in scope
     * @return the iterator
     * @throws IOException if an I/O error occurs
     */
    public BarjCargoArchiveEntryIterator getIteratorForScope(
            final @NonNull Set<String> archiveEntriesInScope) throws IOException {
        final var orderedMatches = getMatchingEntriesInOrderOfOccurrence(archiveEntriesInScope);
        if (orderedMatches.isEmpty()) {
            return new BarjCargoArchiveEntryIterator(this, Collections.emptyList());
        }
        final var first = orderedMatches.get(0).getContentOrElseMetadata();
        final var last = orderedMatches.get(orderedMatches.size() - 1).getMetadata();
        final var boundary = BarjCargoEntryBoundaries.builder()
                .absoluteStartIndexInclusive(first.getAbsoluteStartIndexInclusive())
                .chunkRelativeStartIndexInclusive(first.getChunkRelativeStartIndexInclusive())
                .startChunkName(first.getStartChunkName())
                .absoluteEndIndexExclusive(last.getAbsoluteEndIndexExclusive())
                .chunkRelativeEndIndexExclusive(last.getChunkRelativeEndIndexExclusive())
                .endChunkName(last.getEndChunkName())
                .build();
        final var files = getFilesFor(boundary);
        final var remainingEntities = entityIndexes.stream()
                .filter(index -> index.getContentOrElseMetadata()
                        .getAbsoluteStartIndexInclusive() >= boundary.getAbsoluteStartIndexInclusive())
                .filter(index -> index.getMetadata()
                        .getAbsoluteEndIndexExclusive() <= boundary.getAbsoluteEndIndexExclusive())
                .toList();
        return new BarjCargoArchiveEntryIterator(this, files, remainingEntities);
    }

    /**
     * Returns the matching entries in order of occurrence in the archive.
     *
     * @param archiveEntriesInScope the entries in scope
     * @return the matching entries
     */
    public @NonNull List<BarjCargoEntityIndex> getMatchingEntriesInOrderOfOccurrence(
            final @NonNull Set<String> archiveEntriesInScope) {
        final var normalized = archiveEntriesInScope.stream()
                .map(FilenameUtils::normalizeNoEndSeparator)
                .map(FilenameUtils::separatorsToUnix)
                .collect(Collectors.toSet());
        return entityIndexes.stream()
                .filter(index -> normalized.contains(index.getPath()))
                .toList();
    }

    /**
     * Returns the {@link InputStream} based on the provided boundaries of the entry.
     *
     * @param boundary the boundaries
     * @param key      the key which can decrypt the content
     * @return the decompressed and decrypted {@link InputStream} for the entry
     * @throws IOException If the entry cannot be read
     */
    public InputStream getStreamFor(
            final @NonNull BarjCargoEntryBoundaries boundary,
            final @Nullable SecretKey key) throws IOException {
        final var files = getFilesFor(boundary);
        MergingFileInputStream merging = null;
        InputStream originalDataStream = null;
        try {
            merging = new MergingFileInputStream(files);
            final var skipBytes = boundary.getChunkRelativeStartIndexInclusive();
            final var length = boundary.getArchivedSizeBytes();
            final var hash = boundary.getOriginalHash();
            final var transformations = restoreTransformationSteps(key, skipBytes, length);
            originalDataStream = new CompositeRestoreStream(merging, hashAlgorithm, transformations, hash);
            return originalDataStream;
        } catch (final Exception e) {
            IOUtils.closeQuietly(originalDataStream);
            IOUtils.closeQuietly(merging);
            throw new IOException("Cannot read chunk", e);
        }
    }

    /**
     * Returns the {@link InputStream} based on the provided boundaries of the next entry.
     * The merging input stream parameter is already positioned at the start of the entry.
     *
     * @param mergingInputStream the merging input stream
     * @param boundary           the boundaries
     * @param key                the key which can decrypt the content
     * @return the decompressed and decrypted {@link InputStream} for the entry
     * @throws IOException If the entry cannot be read
     */
    public InputStream getNextStreamFor(
            final @NonNull InputStream mergingInputStream,
            final @NonNull BarjCargoEntryBoundaries boundary,
            final @Nullable SecretKey key) throws IOException {
        InputStream shielded = null;
        InputStream originalDataStream = null;
        try {
            shielded = CloseShieldInputStream.wrap(mergingInputStream);
            final var hash = boundary.getOriginalHash();
            final var length = boundary.getArchivedSizeBytes();
            final var transformations = restoreTransformationSteps(key, 0L, length);
            originalDataStream = new CompositeRestoreStream(shielded, hashAlgorithm, transformations, hash);
            return originalDataStream;
        } catch (final Exception e) {
            IOUtils.closeQuietly(originalDataStream);
            IOUtils.closeQuietly(shielded);
            throw new IOException("Cannot read chunk", e);
        }
    }

    /**
     * Verifies the integrity of the archive by reading the hashes of all entries in the archive.
     *
     * @throws IOException               If the archive cannot be read
     * @throws ArchiveIntegrityException If the archive is in an invalid state
     */
    public void verifyHashes() throws IOException, ArchiveIntegrityException {
        if (hashAlgorithm == null) {
            return;
        }
        final var allFiles = getAllFiles();
        var valid = true;
        try (var mergingStream = new MergingFileInputStream(allFiles)) {
            for (final var entityIndex : entityIndexes) {
                valid &= isArchiveHashValid(entityIndex.getPath(), entityIndex.getContent(), mergingStream);
                valid &= isArchiveHashValid(entityIndex.getPath(), entityIndex.getMetadata(), mergingStream);
            }
            if (mergingStream.read() != -1) {
                log.error("Additional data found after the last entity. Either the archival was incomplete or the archive is corrupted.");
                valid = false;
            }
        }
        if (!valid) {
            throw new ArchiveIntegrityException("Archive integrity check failed. Please check logs for details!");
        }
    }

    /**
     * Opens a stream that can sequentially read the content of the archive.
     *
     * @return the stream
     * @throws IOException If the archive cannot be read
     */
    public InputStream openStreamForSequentialAccess() throws IOException {
        final var fileInputStream = new MergingFileInputStream(this.getAllFiles());
        return BoundedInputStream.builder().setInputStream(fileInputStream).get();
    }

    /**
     * Opens a stream that can sequentially read the content of the archive. The stream will be
     * positioned at the start of the first entry.
     *
     * @param relevantFiles the relevant files of the archive
     * @param list          the list of entity indexes
     * @return the stream
     * @throws IOException If the archive cannot be read
     */
    public InputStream openStreamForSequentialAccess(
            final @NonNull List<Path> relevantFiles,
            final @NonNull List<BarjCargoEntityIndex> list) throws IOException {
        @SuppressWarnings("java:S2095") //the stream will be wrapped
        final var fileInputStream = new MergingFileInputStream(relevantFiles);
        final var start = list.get(0).getContentOrElseMetadata();
        final var skip = start.getChunkRelativeStartIndexInclusive();
        final var end = list.get(list.size() - 1).getMetadata();
        final var length = end.getAbsoluteEndIndexExclusive() - start.getAbsoluteStartIndexInclusive();
        return new FixedRangeInputStream(fileInputStream, skip, length);
    }

    /**
     * Reads the properties from the index file.
     *
     * @param config    The configuration
     * @param indexFile The index file
     * @return the properties
     * @throws IOException If the index file cannot be read
     */
    protected Properties readProperties(
            final @NotNull BarjCargoInputStreamConfiguration config,
            final @NotNull Path indexFile) throws IOException {
        try (var indexStream = new FileInputStream(indexFile.toFile());
             var indexBufferedStream = new BufferedInputStream(indexStream);
             var indexEncryptionStream = newCipherInputStream(config.getIndexDecryptionKey()).decorate(indexBufferedStream);
             var indexCompressionStream = config.getCompressionFunction().decorate(indexEncryptionStream);
             var indexStreamReader = new InputStreamReader(indexCompressionStream, StandardCharsets.UTF_8)) {
            final var properties = new Properties();
            properties.load(indexStreamReader);
            return properties;
        } catch (final Exception e) {
            throw new IOException("Cannot read index file", e);
        }
    }

    /**
     * Parses the entity indexes from the index properties.
     *
     * @param properties the properties
     * @return the entity indexes
     */
    protected @NotNull List<BarjCargoEntityIndex> parseEntityIndexes(
            final @NotNull Properties properties) {
        final var index = parse(properties);
        return LongStream.rangeClosed(1L, index.getTotalEntities())
                .mapToObj(BarjCargoUtil::entryIndexPrefix)
                .map(index::entity)
                .toList();
    }

    /**
     * Generates the file path map of the archive chunks.
     *
     * @param properties the properties
     * @param config     the configuration
     * @return the file path map
     */
    protected @NotNull SortedMap<String, Path> generateFilePathMap(
            final @NotNull Properties properties,
            final @NotNull BarjCargoInputStreamConfiguration config) {
        final var index = parse(properties);
        final var map = new TreeMap<String, Path>();
        IntStream.rangeClosed(1, index.getNumberOfChunks())
                .mapToObj(i -> toChunkFileName(config.getPrefix(), i))
                .map(p -> Path.of(config.getFolder().toAbsolutePath().toString(), p))
                .map(Path::toAbsolutePath)
                .forEach(path -> map.put(path.getFileName().toString(), path));
        return Collections.unmodifiableSortedMap(map);
    }

    /**
     * Verifies the files exist and have the expected sizes.
     *
     * @param properties the properties
     * @param chunkPaths the chunk paths
     * @throws ArchiveIntegrityException If the archive is in an invalid state
     */
    protected void verifyFilesExistAndHaveExpectedSizes(
            final @NotNull Properties properties,
            final @NotNull SortedMap<String, Path> chunkPaths) throws ArchiveIntegrityException {
        final var index = parse(properties);
        var totalSize = 0L;
        final var iterator = chunkPaths.keySet().iterator();
        while (iterator.hasNext()) {
            final var key = iterator.next();
            final var path = chunkPaths.get(key);
            totalSize += verifiedFileSize(index, path, iterator.hasNext());
        }
        if (totalSize != index.getTotalSize()) {
            throw new ArchiveIntegrityException(
                    "Total size is wrong: " + totalSize + " bytes, expected: " + index.getTotalSize() + " bytes.");
        }
    }

    private static long verifiedFileSize(
            final ReadOnlyArchiveIndex index,
            final Path path,
            final boolean isNotLast) {
        final var file = path.toFile();
        if (!file.exists()) {
            throw new ArchiveIntegrityException("Chunk file does not exist: " + path);
        }
        final long expectedSize;
        if (isNotLast) {
            expectedSize = index.getMaxChunkSizeInBytes();
        } else {
            expectedSize = index.getLastChunkSizeInBytes();
        }
        final var fileSize = file.length();
        if (expectedSize != fileSize) {
            throw new ArchiveIntegrityException("Chunk file size is wrong: " + path
                    + ", expected: " + expectedSize + " bytes, actual: " + fileSize + " bytes.");
        }
        return fileSize;
    }

    private static ReadOnlyArchiveIndex parse(final Properties properties) {
        return IndexVersion.forVersionString(properties.getProperty(INDEX_VERSION))
                .createIndex(properties);
    }

    private void validateEntityIndexes(
            final @NotNull List<BarjCargoEntityIndex> entityIndexes) {
        final var paths = entityIndexes.stream()
                .map(BarjCargoEntityIndex::getPath)
                .map(FilenameUtils::separatorsToUnix)
                .collect(Collectors.toSet());
        final var slipping = paths.stream()
                .filter(path -> !path.equals(separatorsToUnix(normalizeNoEndSeparator(path))))
                .collect(Collectors.toSet());
        if (!slipping.isEmpty()) {
            throw new ArchiveIntegrityException("The following paths are not normalized: " + getExamples(slipping));
        }
    }

    private String getExamples(final Set<String> slippingSet) {
        var examples = slippingSet.stream()
                .limit(MAX_NUMBER_OF_EXAMPLES)
                .collect(Collectors.joining(", "));
        if (slippingSet.size() > MAX_NUMBER_OF_EXAMPLES) {
            examples += ", ... +" + (slippingSet.size() - MAX_NUMBER_OF_EXAMPLES) + " more";
        }
        return examples;
    }

    private @NotNull List<Path> getAllFiles() {
        return chunkPaths.values().stream()
                .sorted()
                .toList();
    }

    private @NotNull List<IoFunction<InputStream, InputStream>> restoreTransformationSteps(
            final @Nullable SecretKey key,
            final long skipBytes,
            final long length) {
        return List.of(input -> new FixedRangeInputStream(input, skipBytes, length),
                newCipherInputStream(key),
                decompressionFunction
        );
    }

    private List<Path> getFilesFor(final @NotNull BarjCargoEntryBoundaries boundary) {
        return chunkPaths.subMap(boundary.getStartChunkName(), boundary.getEndChunkName() + "_include_end")
                .values().stream()
                .toList();
    }

    @SuppressWarnings("java:S4087") //the stream should be closed to calculate accurate digest
    private boolean isArchiveHashValid(
            final @NotNull String path,
            final @NotNull BarjCargoEntryBoundaries entry,
            final @NotNull MergingFileInputStream mergingStream) throws IOException {
        try (var digestCalculatorStream = new OptionalDigestOutputStream(OutputStream.nullOutputStream(), hashAlgorithm)) {
            final var remaining = entry.getArchivedSizeBytes();
            copyNBytes(mergingStream, digestCalculatorStream, remaining);
            digestCalculatorStream.flush();
            digestCalculatorStream.close();
            final var actualDigestValue = digestCalculatorStream.getDigestValue();
            if (!Objects.equals(actualDigestValue, entry.getArchivedHash())) {
                log.error("Hash mismatch for {}", path);
                return false;
            } else {
                return true;
            }
        }
    }

    private void copyNBytes(
            final @NotNull MergingFileInputStream from,
            final @NotNull OptionalDigestOutputStream to,
            final long n) throws IOException {
        for (var i = n; i > 0; i -= MEBIBYTE) {
            final var bufferSize = (int) Math.min(MEBIBYTE, i);
            to.write(from.readNBytes(bufferSize));
        }
    }
}
