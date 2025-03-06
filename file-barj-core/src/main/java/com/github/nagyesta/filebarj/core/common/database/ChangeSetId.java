package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;

import java.io.Closeable;
import java.util.UUID;

public interface ChangeSetId extends Closeable {

    UUID id();

    static ChangeSetId of(final ManifestDatabase manifestDatabase) {
        return new ChangeSetIdImpl(manifestDatabase);
    }
}
