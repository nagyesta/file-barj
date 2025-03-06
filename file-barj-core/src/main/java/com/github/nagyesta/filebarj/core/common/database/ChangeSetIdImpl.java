package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import lombok.NonNull;

import java.util.UUID;

public record ChangeSetIdImpl(@NonNull UUID id, @NonNull ManifestDatabase manifestDatabase) implements ChangeSetId {

    public ChangeSetIdImpl(final ManifestDatabase manifestDatabase) {
        this(UUID.randomUUID(), manifestDatabase);
    }

    @Override
    public void close() {
        manifestDatabase.deleteChangeSet(this);
    }
}
