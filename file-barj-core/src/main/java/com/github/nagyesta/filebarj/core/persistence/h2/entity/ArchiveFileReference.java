package com.github.nagyesta.filebarj.core.persistence.h2.entity;

import java.util.UUID;

public record ArchiveFileReference(
        UUID id,
        int backupIncrement,
        UUID file) {
}
