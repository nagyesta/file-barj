package com.github.nagyesta.filebarj.core.persistence.entities;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public record FileMetadataSetId(@NotNull UUID id, @NotNull Consumer<FileMetadataSetId> closeWith)
        implements BaseFileSetId<FileMetadataSetId> {

    public FileMetadataSetId(@NotNull final Consumer<FileMetadataSetId> closeWith) {
        this(UUID.randomUUID(), closeWith);
    }

    @Override
    public void close() {
        closeWith.accept(this);
    }
}
