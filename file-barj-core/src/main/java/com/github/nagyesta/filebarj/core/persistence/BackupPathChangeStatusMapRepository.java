package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import lombok.NonNull;

import java.util.SortedMap;

public interface BackupPathChangeStatusMapRepository
        extends BaseFileMapRepository<BackupPathChangeStatusMapId, BackupPath, Change> {

    SortedMap<Change, Long> countsByStatus(@NonNull BackupPathChangeStatusMapId id);

    Change getOrDefault(BackupPathChangeStatusMapId id, BackupPath path, Change defaultValue);
}

