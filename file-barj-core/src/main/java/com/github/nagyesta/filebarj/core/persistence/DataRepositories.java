package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFileSetRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * The single source where all repository classes are collected.
 */
@Getter
@AllArgsConstructor
public enum DataRepositories {

    /**
     * Uses in-memory implementations for each repository.
     */
    @SuppressWarnings({"checkstyle:MagicNumber"})
    IN_MEMORY(100L, new InMemoryFileSetRepository());

    private final long defaultPageSize;
    @NonNull
    private final FileSetRepository fileSetRepository;

}
