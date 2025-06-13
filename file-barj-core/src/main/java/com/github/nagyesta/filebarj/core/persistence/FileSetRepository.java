package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.FileSetId;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface FileSetRepository {

    /**
     * The default page size used when not specified during looping.
     */
    long PAGE_SIZE = 100L;

    FileSetId createFileSet();

    void appendTo(FileSetId id, Path path);

    void appendTo(FileSetId id, Collection<Path> paths);

    Optional<Path> takeFirst(FileSetId id);

    void removeFileSet(FileSetId id);

    default List<Path> findAll(final FileSetId id, final long offset, final long limit) {
        return findAll(id, offset, limit, SortOrder.ASC);
    }

    List<Path> findAll(FileSetId id, long offset, long limit, SortOrder sortOrder);

    long countAll(FileSetId id);

    boolean isEmpty(FileSetId id);

    List<Path> detectCaseInsensitivityIssues(FileSetId id);

    default void forEach(final FileSetId fileSetId, final ForkJoinPool threadPool, final Consumer<Path> consumer) {
        forEach(fileSetId, threadPool, SortOrder.ASC, consumer);
    }

    default void forEachReverse(final FileSetId fileSetId, final ForkJoinPool threadPool, final Consumer<Path> consumer) {
        forEach(fileSetId, threadPool, SortOrder.DESC, consumer);
    }

    void forEach(FileSetId fileSetId, ForkJoinPool threadPool, SortOrder order, Consumer<Path> consumer);

}

