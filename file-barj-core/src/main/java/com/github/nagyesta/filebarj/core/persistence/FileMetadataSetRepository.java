package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;

import java.util.List;
import java.util.Set;
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
}

