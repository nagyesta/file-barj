package com.github.nagyesta.filebarj.core.persistence.entities;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public record ArchivedFileMetadataSetId(@NotNull UUID id, @NotNull Consumer<ArchivedFileMetadataSetId> closeWith)
        implements BaseFileSetId<ArchivedFileMetadataSetId> {

    public ArchivedFileMetadataSetId(@NotNull final Consumer<ArchivedFileMetadataSetId> closeWith) {
        this(UUID.randomUUID(), closeWith);
    }

    @Override
    public void close() {
        closeWith.accept(this);
    }
}
