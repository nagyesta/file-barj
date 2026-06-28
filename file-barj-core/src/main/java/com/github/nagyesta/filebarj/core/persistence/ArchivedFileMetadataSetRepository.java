package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface ArchivedFileMetadataSetRepository
        extends Closeable {

    long countAllFiles(ArchivedFileMetadataSetId id);

    Optional<ArchivedFileMetadata> findByFileMetadataId(ArchivedFileMetadataSetId id, UUID fileMetadataId);

    ArchivedFileMetadataSetId intersectWithFileMetadata(ArchivedFileMetadataSetId id, FileMetadataSetId fileMetadataSetId);

    ArchivedFileMetadataSetId filterByBackupIncrements(ArchivedFileMetadataSetId id, SortedSet<Integer> versions);

    Set<String> asEntryPaths(ArchivedFileMetadataSetId id);

    SortedSet<FileMetadata> findFileMetadataByArchiveLocator(
            ArchivedFileMetadataSetId id, FileMetadataSetId files, ArchiveEntryLocator currentLocator);

    ArchivedFileMetadataSetId copyAll(ArchivedFileMetadataSetId source);

    void registerWith(DataStore dataStore);

    ArchivedFileMetadataSetId createFileSet();

    void appendTo(ArchivedFileMetadataSetId id, ArchivedFileMetadata value);

    void appendTo(ArchivedFileMetadataSetId id, Collection<ArchivedFileMetadata> values);

    void removeFileSet(ArchivedFileMetadataSetId id);

    List<ArchivedFileMetadata> findAll(ArchivedFileMetadataSetId id);

    long countAll(ArchivedFileMetadataSetId id);

    boolean isEmpty(ArchivedFileMetadataSetId id);

    void forEachAsc(ArchivedFileMetadataSetId id, ForkJoinPool threadPool, Consumer<ArchivedFileMetadata> consumer);

    void forEach(ArchivedFileMetadataSetId id, ForkJoinPool threadPool, Consumer<ArchivedFileMetadata> consumer);

    boolean isClosed();

    void assertExists(ArchivedFileMetadataSetId id);
}
