package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;

import java.io.Closeable;
import java.util.Map;
import java.util.SortedMap;

public interface BackupPathChangeStatusMapRepository
        extends Closeable {

    SortedMap<Change, Long> countsByStatus(BackupPathChangeStatusMapId id);

    Change getOrDefault(BackupPathChangeStatusMapId id, BackupPath path, Change defaultValue);

    void registerWith(DataStore dataStore);

    BackupPathChangeStatusMapId createFileMap();

    void appendTo(BackupPathChangeStatusMapId id, BackupPath key, Change value);

    void appendTo(BackupPathChangeStatusMapId id, Map<BackupPath, Change> values);

    void removeFileMap(BackupPathChangeStatusMapId id);

    boolean isClosed();
}

