package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;

import java.io.Closeable;
import java.util.UUID;

public interface ArchiveSetId extends Closeable {

    UUID id();

    static ArchiveSetId of(final ManifestDatabase manifestDatabase) {
        return new ArchiveSetIdImpl(manifestDatabase);
    }
}
