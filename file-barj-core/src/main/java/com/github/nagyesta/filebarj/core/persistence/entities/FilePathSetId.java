package com.github.nagyesta.filebarj.core.persistence.entities;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public record FilePathSetId(@NotNull UUID id, @NotNull Consumer<FilePathSetId> closeWith)
        implements BaseFileSetId<FilePathSetId> {

    public FilePathSetId(@NotNull final Consumer<FilePathSetId> closeWith) {
        this(UUID.randomUUID(), closeWith);
    }

    @Override
    public void close() {
        closeWith.accept(this);
    }
}
