package com.github.nagyesta.filebarj.core.persistence.h2.entity;

import java.util.Collection;
import java.util.UUID;

public record GroupedIdCollection(String key, Collection<UUID> ids) {
}
