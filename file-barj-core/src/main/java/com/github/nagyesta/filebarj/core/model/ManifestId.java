package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.validation.PastOrPresentEpochSeconds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.NonNull;

import java.util.Comparator;
import java.util.SortedSet;

/**
 * Common behavior of manifest identifiers.
 */
public interface ManifestId extends Comparable<ManifestId> {

    /**
     * Returns the version numbers of the backup increments.
     * <br/><br/>
     * THe full backups use the index 0, every subsequent incremental backup increments the version
     * by 1. A manifest can contain more numbers if the backup increments were merged (consolidated)
     * into a single archive.
     *
     * @return versions
     */
    @Valid
    @Size(min = 1)
    @NonNull
    SortedSet<@PositiveOrZero Integer> getVersions();

    /**
     * Returns The time when the backup process was started in UTC epoch
     * seconds.
     *
     * @return start time
     */
    @Positive
    @PastOrPresentEpochSeconds
    long getStartTimeUtcEpochSeconds();

    /**
     * Returns the type of the backup.
     *
     * @return backup type
     */
    @NonNull
    BackupType getBackupType();

    /**
     * Returns the smallest version contained by this manifest.
     *
     * @return min version
     */
    @JsonIgnore
    default int getMinVersion() {
        return getVersions().first();
    }

    /**
     * Returns the biggest version contained by this manifest.
     *
     * @return max version
     */
    @JsonIgnore
    default int getMaxVersion() {
        return getVersions().last();
    }

    @Override
    default int compareTo(@NonNull final ManifestId o) {
        return Comparator.comparing(ManifestId::getStartTimeUtcEpochSeconds)
                .thenComparing(ManifestId::getMinVersion)
                .thenComparing(ManifestId::getMaxVersion)
                .compare(this, o);
    }
}
