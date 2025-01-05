package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.validation.PastOrPresentEpochSeconds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Identifier for backup manifests.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ImmutableManifestId implements ManifestId {

    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    @PastOrPresentEpochSeconds
    private final @Positive long startTimeUtcEpochSeconds;
    /**
     * The type of the backup.
     */
    private final @NonNull BackupType backupType;
    /**
     * The version numbers of the backup increments.
     * <br/><br/>
     * THe full backups use the index 0, every subsequent incremental backup increments the version
     * by 1. A manifest can contain more numbers if the backup increments were merged (consolidated)
     * into a single archive.
     */
    private final @Valid
    @Size(min = 1)
    @NonNull SortedSet<@PositiveOrZero Integer> versions;

    private ImmutableManifestId(
            @Valid @Size(min = 1) @NonNull final SortedSet<@PositiveOrZero Integer> versions,
            @NonNull @Positive final Long startTimeUtcEpochSeconds,
            @NonNull final BackupType backupType) {
        this.versions = versions;
        this.startTimeUtcEpochSeconds = startTimeUtcEpochSeconds;
        this.backupType = backupType;
    }

    @Override
    public String toString() {
        return "ImmutableManifestId{"
                + "startTimeUtcEpochSeconds=" + startTimeUtcEpochSeconds
                + ", backupType=" + backupType
                + ", versions=" + versions
                + '}';
    }

    public static ImmutableManifestIdBuilder builder() {
        return new ImmutableManifestIdBuilder();
    }

    public static ImmutableManifestId of(@NonNull final ManifestId manifestId) {
        if (manifestId instanceof ImmutableManifestId) {
            return (ImmutableManifestId) manifestId;
        } else {
            return ImmutableManifestId.builder()
                    .startTimeUtcEpochSeconds(manifestId.getStartTimeUtcEpochSeconds())
                    .versions(manifestId.getVersions())
                    .backupType(manifestId.getBackupType())
                    .build();
        }
    }

    public static class ImmutableManifestIdBuilder {
        private Long startTimeUtcEpochSeconds;
        private BackupType backupType;
        private SortedSet<Integer> versions;

        ImmutableManifestIdBuilder() {
        }

        public ImmutableManifestIdBuilder versions(@NonNull final SortedSet<Integer> versions) {
            if (versions.isEmpty()) {
                throw new IllegalArgumentException("At least one version must be provided");
            }
            if (versions.stream().anyMatch(i -> i == null || i < 0)) {
                throw new IllegalArgumentException("Versions must be at least zero non-null values");
            }
            if (versions.last() - versions.first() != versions.size() - 1) {
                throw new IllegalArgumentException("The versions mst be continuously sorted");
            }
            this.versions = Collections.unmodifiableSortedSet(new TreeSet<>(versions));
            return this;
        }

        public ImmutableManifestIdBuilder startTimeUtcEpochSeconds(final long startTimeUtcEpochSeconds) {
            if (startTimeUtcEpochSeconds < 0) {
                throw new IllegalArgumentException("Start time utc epoch seconds must be non-negative");
            }
            this.startTimeUtcEpochSeconds = startTimeUtcEpochSeconds;
            return this;
        }

        public ImmutableManifestIdBuilder backupType(@NonNull final BackupType backupType) {
            this.backupType = backupType;
            return this;
        }

        public ImmutableManifestId build() {
            if (backupType == BackupType.FULL && !versions.contains(0)) {
                throw new IllegalStateException("The 0 version must be present for full backups");
            } else if (backupType != BackupType.FULL && versions.contains(0)) {
                throw new IllegalStateException("The 0 version is reserved for full backups");
            }
            return new ImmutableManifestId(this.versions, this.startTimeUtcEpochSeconds, this.backupType);
        }

        @Override
        public String toString() {
            return "ImmutableManifestIdBuilder{"
                    + "startTimeUtcEpochSeconds=" + startTimeUtcEpochSeconds
                    + ", backupType=" + backupType
                    + ", versions=" + versions
                    + '}';
        }
    }
}
