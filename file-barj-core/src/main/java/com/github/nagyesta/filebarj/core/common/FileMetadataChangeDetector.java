package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Detects changes compared to previous backup increments.
 */
public interface FileMetadataChangeDetector {

    /**
     * Determines if the file metadata has changed since the previous variant of the same file.
     * <br/>
     * Focuses on permissions, owner, dates.
     *
     * @param previousMetadata The previous metadata version
     * @param currentMetadata  The current metadata version
     * @return true if the relevant file metadata has changed
     */
    boolean hasMetadataChanged(@NotNull FileMetadata previousMetadata, @NotNull FileMetadata currentMetadata);

    /**
     * Determines if the content of the file has changed.
     *
     * @param previousMetadata The previous metadata version
     * @param currentMetadata  The current metadata version
     * @return true if the file content has changed
     */
    boolean hasContentChanged(@NotNull FileMetadata previousMetadata, @NotNull FileMetadata currentMetadata);

    /**
     * Determines if the file is from the last manifest known to the current
     * {@link FileMetadataChangeDetector} instance.
     *
     * @param fileMetadata The metadata version
     * @return true if the file is from the last manifest
     */
    boolean isFromLastIncrement(@NotNull FileMetadata fileMetadata);

    /**
     * Finds the most relevant previous version of the file based on the previous manifests known to
     * the {@link FileMetadataChangeDetector} instance.
     *
     * @param currentMetadata The current metadata version
     * @return the metadata instance that is the most relevant previous version of the file
     */
    @Nullable
    FileMetadata findMostRelevantPreviousVersion(@NotNull FileMetadata currentMetadata);

    /**
     * Finds the previous version of the file based on absolute path.
     *
     * @param absolutePath The path of the file
     * @return the file metadata
     */
    @Nullable
    FileMetadata findPreviousVersionByAbsolutePath(@NotNull Path absolutePath);

    /**
     * Classifies the nature of the change between the previous and current metadata versions.
     *
     * @param previousMetadata The previous metadata version
     * @param currentMetadata  The current metadata version
     * @return the type of the change
     */
    @NotNull
    Change classifyChange(@NotNull FileMetadata previousMetadata, @NotNull FileMetadata currentMetadata);
}
