package com.github.nagyesta.filebarj.core.persistence.entities;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public record BackupPathChangeStatusMapId(@NotNull UUID id, @NotNull Consumer<BackupPathChangeStatusMapId> closeWith)
        implements BaseFileSetId<BackupPathChangeStatusMapId> {

    public BackupPathChangeStatusMapId(@NotNull final Consumer<BackupPathChangeStatusMapId> closeWith) {
        this(UUID.randomUUID(), closeWith);
    }

    @Override
    public void close() {
        closeWith.accept(this);
    }
}
