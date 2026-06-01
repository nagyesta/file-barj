package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.FileMetadataIndex;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface FileMetadataSetRepository
        extends Closeable {

    long countByType(FileMetadataSetId id, Collection<FileType> types);

    SortedMap<FileType, Long> countsByType(FileMetadataSetId id);

    SortedMap<Change, Long> countsByStatus(FileMetadataSetId id);

    void forEachByChangeStatusesAndFileTypesAsc(
            FileMetadataSetId id,
            Set<Change> changeStatuses,
            Set<FileType> fileTypes,
            ForkJoinPool threadPool,
            Consumer<FileMetadata> consumer);

    void forEachByChangeStatusesAndFileTypesDesc(
            FileMetadataSetId id,
            Set<Change> changeStatuses,
            Set<FileType> fileTypes,
            ForkJoinPool threadPool,
            Consumer<FileMetadata> consumer);

    void forEachDuplicateOf(
            FileMetadataSetId id,
            Set<Change> changeStatuses,
            Set<FileType> fileTypes,
            DuplicateHandlingStrategy strategy,
            HashAlgorithm hashAlgorithm,
            Consumer<List<List<FileMetadata>>> consumer);

    long getOriginalSizeBytes(FileMetadataSetId id);

    boolean containsFileId(FileMetadataSetId id, UUID fileId);

    List<FileMetadata> findErrorsOf(FileMetadataSetId id);

    void updateArchiveMetadataId(FileMetadataSetId id, UUID metadataId, @Nullable UUID archiveMetadataId);

    FileMetadataSetId intersectByPath(FileMetadataSetId filesFromLastIncrement, FileMetadataSetId restoreScope);

    FileMetadataSetId keepChangedContent(FileMetadataSetId id, BackupPathChangeStatusMapId changeStats);

    FileMetadataSetId keepChangedMetadata(FileMetadataSetId id, Set<FileType> fileTypes, BackupPathChangeStatusMapId changeStats);

    Optional<FileMetadata> findFileById(UUID id, UUID file);

    SortedSet<FileMetadata> findFilesByIds(FileMetadataSetId id, Set<UUID> files);

    boolean containsPath(FileMetadataSetId id, String absolutePath);

    FileMetadataSetId copyAllNotDeleted(FileMetadataSetId source);

    void copyAll(FileMetadataSetId source, FileMetadataSetId target);

    void forEachForIndex(FileMetadataSetId id, Consumer<FileMetadataIndex> consumer);

    void registerWith(DataStore dataStore);

    FileMetadataSetId createFileSet();

    void appendTo(FileMetadataSetId id, FileMetadata value);

    void appendTo(FileMetadataSetId id, Collection<FileMetadata> values);

    void removeFileSet(FileMetadataSetId id);

    List<FileMetadata> findAll(FileMetadataSetId id);

    long countAll(FileMetadataSetId id);

    boolean isEmpty(FileMetadataSetId id);

    void forEachAsc(FileMetadataSetId id, ForkJoinPool threadPool, Consumer<FileMetadata> consumer);

    void forEach(FileMetadataSetId id, ForkJoinPool threadPool, Consumer<FileMetadata> consumer);

    boolean isClosed();

    void assertExists(FileMetadataSetId id);
}
