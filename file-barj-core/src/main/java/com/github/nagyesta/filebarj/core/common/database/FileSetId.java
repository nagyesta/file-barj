package com.github.nagyesta.filebarj.core.common.database;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;

import java.io.Closeable;
import java.util.UUID;

public interface FileSetId extends Closeable {

    UUID id();

    static FileSetId of(final ManifestDatabase manifestDatabase) {
        return new FileSetIdImpl(manifestDatabase);
    }
}
