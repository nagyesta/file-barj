package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.FileSetRepository;
import com.github.nagyesta.filebarj.core.persistence.SortOrder;
import com.github.nagyesta.filebarj.core.persistence.entities.FileSetId;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class InMemoryFileSetRepository implements FileSetRepository {

    private final Map<UUID, Queue<Path>> fileSets = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public FileSetId createFileSet() {
        final var fileSetId = new FileSetId(this::removeFileSet);
        fileSets.put(fileSetId.id(), new LinkedList<>());
        locks.put(fileSetId.id(), new ReentrantLock());
        return fileSetId;
    }

    @Override
    public void appendTo(
            final @NonNull FileSetId id,
            final @NonNull Path path) {
        appendTo(id, Collections.singletonList(path));
    }

    @Override
    public void appendTo(
            final @NonNull FileSetId id,
            final @NonNull Collection<Path> paths) {
        final var lock = findLockFor(id);
        lock.lock();
        try {
            if (fileSets.containsKey(id.id())) {
                final var fileSet = fileSets.get(id.id());
                paths.forEach(path -> {
                    if (!fileSet.contains(path)) {
                        fileSet.add(path);
                    }
                });
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Path> takeFirst(final @NonNull FileSetId id) {
        final var lock = findLockFor(id);
        lock.lock();
        try {
            final var paths = fileSets.get(id.id());
            return Optional.ofNullable(paths.poll());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeFileSet(final @NonNull FileSetId id) {
        final var lock = findLockFor(id);
        lock.lock();
        try {
            fileSets.remove(id.id());
            locks.remove(id.id());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Path> findAll(
            final @NonNull FileSetId id,
            final long offset,
            final long limit,
            final @NonNull SortOrder order) {
        final var lock = findLockFor(id);
        lock.lock();
        try {
            return fileSets.get(id.id()).stream()
                    .sorted(orderBy(order))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long countAll(final @NonNull FileSetId id) {
        final var lock = findLockFor(id);
        lock.lock();
        try {
            return fileSets.get(id.id()).size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty(final @NonNull FileSetId id) {
        return countAll(id) == 0L;
    }

    @Override
    public List<Path> detectCaseInsensitivityIssues(final @NonNull FileSetId id) {
        final var lock = findLockFor(id);
        lock.lock();
        try {
            return fileSets.get(id.id()).stream()
                    .collect(Collectors.groupingBy(path -> path.toString().toLowerCase()))
                    .values().stream()
                    .filter(paths -> paths.size() > 1)
                    .flatMap(Collection::stream)
                    .sorted()
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void forEach(
            final @NonNull FileSetId fileSetId,
            final @NonNull ForkJoinPool threadPool,
            final @NonNull SortOrder order,
            final @NonNull Consumer<Path> consumer) {
        final var countAll = countAll(fileSetId);
        LongStream.iterate(0L, offset -> offset < countAll, offset -> offset + PAGE_SIZE)
                .mapToObj(offset -> findAll(fileSetId, offset, PAGE_SIZE, order))
                .forEach(paths -> threadPool.submit(() -> paths.stream().parallel().forEach(consumer)).join());
    }

    private ReentrantLock findLockFor(final @NotNull FileSetId id) {
        final var lock = locks.get(id.id());
        if (lock == null) {
            throw new IllegalStateException("Failed to obtain locks for: " + id);
        }
        return lock;
    }

    private Comparator<Path> orderBy(final @NotNull SortOrder order) {
        return switch (order) {
            case ASC -> Comparator.naturalOrder();
            case DESC -> Comparator.reverseOrder();
        };
    }
}
