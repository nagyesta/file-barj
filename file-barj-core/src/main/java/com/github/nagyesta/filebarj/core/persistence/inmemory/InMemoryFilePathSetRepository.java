package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.FilePathSetRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.FilePathSetId;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InMemoryFilePathSetRepository
        extends InMemoryBaseFileSetRepository<FilePathSetId, Path>
        implements FilePathSetRepository {

    @Override
    protected FilePathSetId createFileSetId(final Consumer<FilePathSetId> closeWith) {
        return new FilePathSetId(closeWith);
    }

    @Override
    public Optional<Path> takeFirst(final @NotNull FilePathSetId id) {
        final var values = getFileSetById(id);
        final var iterator = values.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        final var next = Optional.ofNullable(iterator.next());
        iterator.remove();
        return next;
    }

    @Override
    public List<Path> detectCaseInsensitivityIssues(final @NotNull FilePathSetId id) {
        return getFileSetById(id).stream()
                .collect(Collectors.groupingBy(path -> path.toString().toLowerCase()))
                .values().stream()
                .filter(paths -> paths.size() > 1)
                .flatMap(Collection::stream)
                .sorted()
                .toList();
    }

    @Override
    public FilePathSetId subtract(
            final @NotNull FilePathSetId fromSet,
            final @NotNull FilePathSetId removeSet) {
        final var toRemove = getFileSetById(removeSet);
        final var result = createFileSet();
        getFileSetById(fromSet)
                .stream()
                .filter(path -> !toRemove.contains(path))
                .forEach(path -> appendTo(result, path));
        return result;
    }
}
