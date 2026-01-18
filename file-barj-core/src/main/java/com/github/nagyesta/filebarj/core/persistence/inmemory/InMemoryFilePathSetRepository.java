package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InMemoryFilePathSetRepository extends InMemoryBaseFileSetRepository<FilePathSetId, Path> implements FilePathSetRepository {

    @Override
    protected FilePathSetId createFileSetId(final Consumer<FilePathSetId> closeWith) {
        return new FilePathSetId(closeWith);
    }

    @Override
    public List<Path> detectCaseInsensitivityIssues(final @NonNull FilePathSetId id) {
        return getFileSetById(id).stream()
                .collect(Collectors.groupingBy(path -> path.toString().toLowerCase()))
                .values().stream()
                .filter(paths -> paths.size() > 1)
                .flatMap(Collection::stream)
                .sorted()
                .toList();
    }
}
