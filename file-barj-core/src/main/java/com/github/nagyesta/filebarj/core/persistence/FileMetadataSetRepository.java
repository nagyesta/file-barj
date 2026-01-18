package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface FileMetadataSetRepository extends BaseFileSetRepository<FileMetadataSetId, FileMetadata> {

    default void forEachByChangeStatusesAndFileTypes(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final ForkJoinPool threadPool,
            final Consumer<FileMetadata> consumer) {
        forEachByChangeStatusesAndFileTypes(id, changeStatuses, fileTypes, threadPool, SortOrder.ASC, consumer);
    }

    default void forEachByChangeStatusesAndFileTypesReverse(
            final FileMetadataSetId id,
            final Set<Change> changeStatuses,
            final Set<FileType> fileTypes,
            final ForkJoinPool threadPool,
            final Consumer<FileMetadata> consumer) {
        forEachByChangeStatusesAndFileTypes(id, changeStatuses, fileTypes, threadPool, SortOrder.DESC, consumer);
    }

    long countByType(FileMetadataSetId id, Collection<FileType> types);

    SortedMap<FileType, Long> countsByType(FileMetadataSetId id);

    SortedMap<Change, Long> countsByStatus(FileMetadataSetId id);

    void forEachByChangeStatusesAndFileTypes(
            FileMetadataSetId id,
            Set<Change> changeStatuses,
            Set<FileType> fileTypes,
            ForkJoinPool threadPool,
            SortOrder order,
            Consumer<FileMetadata> consumer);

    void forEachDuplicateOf(
            FileMetadataSetId id,
            Set<Change> changeStatuses,
            Set<FileType> fileTypes,
            DuplicateHandlingStrategy strategy,
            HashAlgorithm hashAlgorithm,
            ForkJoinPool threadPool,
            Consumer<List<List<FileMetadata>>> consumer);

    long getOriginalSizeBytes(FileMetadataSetId id);

    boolean containsFileId(FileMetadataSetId id, UUID fileId);

    Set<FileMetadata> findFilesByOriginalHash(FileMetadataSetId id, String originalHash);

    Set<FileMetadata> findFilesByOriginalSize(FileMetadataSetId id, Long originalSize);

    Optional<FileMetadata> findFileByPath(FileMetadataSetId id, BackupPath absolutePath);

    List<FileMetadata> findErrorsOf(FileMetadataSetId id);

    void updateArchiveMetadataId(FileMetadataSetId id, UUID metadataId, @Nullable UUID archiveMetadataId);

    FileMetadataSetId intersectByPath(FileMetadataSetId filesFromLastIncrement, FileMetadataSetId restoreScope);

    FileMetadataSetId keepChangedContent(FileMetadataSetId id, BackupPathChangeStatusMapId changeStats);

    FileMetadataSetId keepChangedMetadata(FileMetadataSetId id, Set<FileType> fileTypes, BackupPathChangeStatusMapId changeStats);

    SortedSet<FileMetadata> findFilesByIds(FileMetadataSetId id, Set<UUID> files);

    boolean containsPath(FileMetadataSetId id, String absolutePath);
}
