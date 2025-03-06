package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import lombok.NonNull;

import java.util.UUID;

public record ArchiveSetIdImpl(@NonNull UUID id, @NonNull ManifestDatabase manifestDatabase) implements ArchiveSetId {

    public ArchiveSetIdImpl(final ManifestDatabase manifestDatabase) {
        this(UUID.randomUUID(), manifestDatabase);
    }

    @Override
    public void close() {
        manifestDatabase.deleteArchiveSet(this);
    }
}
