package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.FileSetId;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileSetRepository {

    FileSetId createFileSet();

    void appendTo(FileSetId id, Path path);

    void appendTo(FileSetId id, Collection<Path> paths);

    Optional<Path> takeFirst(FileSetId id);

    void removeFileSet(FileSetId id);

    List<Path> findAll(FileSetId id, long offset, long limit);

    long countAll(FileSetId id);

    boolean isEmpty(FileSetId id);

    List<Path> detectCaseInsensitivityIssues(FileSetId id);

}

