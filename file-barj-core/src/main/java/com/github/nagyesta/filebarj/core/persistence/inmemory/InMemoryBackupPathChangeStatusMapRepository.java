package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.persistence.BackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import org.jetbrains.annotations.NotNull;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InMemoryBackupPathChangeStatusMapRepository
        extends InMemoryBaseFileMapRepository<BackupPathChangeStatusMapId, BackupPath, Change>
        implements BackupPathChangeStatusMapRepository {

    @Override
    protected BackupPathChangeStatusMapId createFileMapId(final Consumer<BackupPathChangeStatusMapId> closeWith) {
        return new BackupPathChangeStatusMapId(closeWith);
    }

    @Override
    public SortedMap<Change, Long> countsByStatus(final @NotNull BackupPathChangeStatusMapId id) {
        return new TreeMap<>(getFileMapById(id).values()
                .stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
    }

    @Override
    public Change getOrDefault(
            @NotNull final BackupPathChangeStatusMapId id,
            @NotNull final BackupPath path,
            @NotNull final Change defaultValue) {
        return getFileMapById(id).getOrDefault(path, defaultValue);
    }

}
