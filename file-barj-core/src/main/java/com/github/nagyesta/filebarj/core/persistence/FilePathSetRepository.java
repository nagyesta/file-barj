package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface FilePathSetRepository extends BaseFileSetRepository<FilePathSetId, Path> {

    Optional<Path> takeFirst(FilePathSetId id);

    List<Path> detectCaseInsensitivityIssues(FilePathSetId id);

    FilePathSetId subtract(FilePathSetId fromSet, FilePathSetId removeSet);
}

