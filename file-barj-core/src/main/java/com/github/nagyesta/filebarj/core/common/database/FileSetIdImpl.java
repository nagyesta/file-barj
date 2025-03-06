package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import lombok.NonNull;

import java.util.UUID;

public record FileSetIdImpl(@NonNull UUID id, @NonNull ManifestDatabase manifestDatabase) implements FileSetId {

    public FileSetIdImpl(final ManifestDatabase manifestDatabase) {
        this(UUID.randomUUID(), manifestDatabase);
    }

    @Override
    public void close() {
        manifestDatabase.deleteFileSet(this);
    }
}
