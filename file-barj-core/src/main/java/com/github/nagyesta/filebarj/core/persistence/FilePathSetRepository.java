package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;

import java.nio.file.Path;
import java.util.List;

public interface FilePathSetRepository extends BaseFileSetRepository<FilePathSetId, Path> {

    List<Path> detectCaseInsensitivityIssues(FilePathSetId id);

}

