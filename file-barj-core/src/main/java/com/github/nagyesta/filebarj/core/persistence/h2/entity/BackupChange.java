package com.github.nagyesta.filebarj.core.persistence.h2.entity;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import com.github.nagyesta.filebarj.core.model.enums.Change;

public record BackupChange(BackupPath backupPath, Change status) {
}
