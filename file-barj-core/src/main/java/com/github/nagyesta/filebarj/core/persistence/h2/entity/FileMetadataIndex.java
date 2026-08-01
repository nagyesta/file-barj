package com.github.nagyesta.filebarj.core.persistence.h2.entity;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record FileMetadataIndex(
        @NonNull UUID fileSetId,
        @NonNull UUID id,
        @NonNull BackupPath absolutePath,
        @Nullable String originalHash,
        @NotNull @PositiveOrZero Long originalSizeBytes,
        @NotNull Long lastModifiedUtcEpochSeconds,
        @NonNull FileType fileType) {

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (FileMetadataIndex) o;
        return fileType == that.fileType
                && fileType.isContentSource()
                && matchesByContent(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalSizeBytes, fileType);
    }

    private boolean matchesByContent(final FileMetadataIndex that) {
        if (originalHash != null) {
            return matchesByHashAndSize(that);
        } else {
            return matchesBySimpleComparison(that);
        }
    }

    private boolean matchesByHashAndSize(final FileMetadataIndex that) {
        return Objects.equals(originalHash, that.originalHash)
                && Objects.equals(originalSizeBytes, that.originalSizeBytes);
    }

    private boolean matchesBySimpleComparison(final FileMetadataIndex that) {
        return Objects.equals(originalSizeBytes, that.originalSizeBytes)
                && Objects.equals(lastModifiedUtcEpochSeconds, that.lastModifiedUtcEpochSeconds)
                && Objects.equals(absolutePath.getFileName(), that.absolutePath.getFileName());
    }
}
