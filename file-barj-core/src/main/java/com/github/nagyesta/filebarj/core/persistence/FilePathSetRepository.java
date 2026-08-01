package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface FilePathSetRepository
        extends Closeable {

    List<Path> detectCaseInsensitivityIssues(FilePathSetId id);

    FilePathSetId subtract(FilePathSetId fromSet, FilePathSetId removeSet);

    void registerWith(DataStore dataStore);

    FilePathSetId createFileSet();

    void appendTo(FilePathSetId id, Path value);

    void appendTo(FilePathSetId id, Collection<Path> values);

    void removeFileSet(FilePathSetId id);

    List<Path> findAll(FilePathSetId id);

    long countAll(FilePathSetId id);

    boolean isEmpty(FilePathSetId id);

    void forEachAsc(FilePathSetId id, ForkJoinPool threadPool, Consumer<Path> consumer);

    void forEachDesc(FilePathSetId id, ForkJoinPool threadPool, Consumer<Path> consumer);

    void forEach(FilePathSetId id, ForkJoinPool threadPool, Consumer<Path> consumer);

    boolean isClosed();
}

