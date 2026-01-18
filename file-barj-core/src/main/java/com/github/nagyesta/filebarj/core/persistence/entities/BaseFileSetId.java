package com.github.nagyesta.filebarj.core.persistence.entities;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.UUID;
import java.util.function.Consumer;

public interface BaseFileSetId<K extends BaseFileSetId<K>> extends Closeable {

    @NotNull UUID id();

    @NotNull Consumer<K> closeWith();
}
