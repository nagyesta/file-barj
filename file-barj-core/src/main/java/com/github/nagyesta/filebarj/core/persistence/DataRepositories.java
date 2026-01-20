package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFileMetadataSetRepository;
import com.github.nagyesta.filebarj.core.persistence.inmemory.InMemoryFilePathSetRepository;
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
    IN_MEMORY(new InMemoryFilePathSetRepository(), new InMemoryFileMetadataSetRepository());

    @NonNull
    private final FilePathSetRepository filePathSetRepository;

    @NonNull
    private final FileMetadataSetRepository fileMetadataSetRepository;
}
