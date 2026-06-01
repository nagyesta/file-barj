package com.github.nagyesta.filebarj.core.persistence.h2;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.persistence.BackupPathChangeStatusMapRepository;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.h2.entity.BackupChange;
import com.github.nagyesta.filebarj.core.persistence.h2.extension.H2BackupPathChangeStatusMapRepositoryExtension;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class H2BackupPathChangeStatusMapRepository
        implements BackupPathChangeStatusMapRepository {

    private final Jdbi jdbi;
    private final Set<BackupPathChangeStatusMapId> openSets = new CopyOnWriteArraySet<>();
    private DataStore dataStore;

    public H2BackupPathChangeStatusMapRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public BackupPathChangeStatusMapId createFileMap() {
        return createFileMapId(this::removeFileMap);
    }

    @Override
    public void appendTo(
            final @NotNull BackupPathChangeStatusMapId id,
            final @NotNull BackupPath key,
            final @NotNull Change value) {
        appendTo(id, Collections.singletonMap(key, value));
    }

    @Override
    public void appendTo(
            final @NotNull BackupPathChangeStatusMapId id,
            final @NotNull Map<BackupPath, Change> values) {
        jdbi.withExtension(H2BackupPathChangeStatusMapRepositoryExtension.class, handle -> {
            for (final var entry : values.entrySet()) {
                handle.appendTo(id, new BackupChange(entry.getKey(), entry.getValue()));
            }
            return null;
        });
    }

    @Override
    public void removeFileMap(final @NotNull BackupPathChangeStatusMapId id) {
        jdbi.withExtension(H2BackupPathChangeStatusMapRepositoryExtension.class, handle -> {
            handle.deleteAll(id);
            return null;
        });
    }

    @Override
    public SortedMap<Change, Long> countsByStatus(final BackupPathChangeStatusMapId id) {
        return jdbi.withExtension(H2BackupPathChangeStatusMapRepositoryExtension.class, handle -> handle
                .countsByStatus(id));
    }

    @Override
    public Change getOrDefault(
            final BackupPathChangeStatusMapId id,
            final BackupPath path,
            final Change defaultValue) {
        return jdbi.withExtension(H2BackupPathChangeStatusMapRepositoryExtension.class, handle -> handle
                .getStatus(id, path))
                .map(name -> Enum.valueOf(Change.class, name))
                .orElse(defaultValue);
    }

    @Override
    public boolean isClosed() {
        return openSets.isEmpty();
    }

    @Override
    public void close() {
        openSets.forEach(this::removeFileMap);
        openSets.clear();
    }

    protected DataStore dataStore() {
        return dataStore;
    }

    private BackupPathChangeStatusMapId createFileMapId(final Consumer<BackupPathChangeStatusMapId> closeWith) {
        final var id = new BackupPathChangeStatusMapId(closeWith);
        openSets.add(id);
        return id;
    }
}
