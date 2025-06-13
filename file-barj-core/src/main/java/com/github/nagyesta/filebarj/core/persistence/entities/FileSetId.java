package com.github.nagyesta.filebarj.core.persistence.entities;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public record FileSetId(@NotNull UUID id, @NotNull Consumer<FileSetId> closeWith) implements AutoCloseable {
    public FileSetId(@NotNull final Consumer<FileSetId> closeWith) {
        this(UUID.randomUUID(), closeWith);
    }

    @Override
    public void close() {
        closeWith.accept(this);
    }
}
