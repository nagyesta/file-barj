package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.model.ArchiveEntryLocator;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;

import java.util.*;

public interface ArchivedFileMetadataSetRepository
        extends BaseFileSetRepository<ArchivedFileMetadataSetId, ArchivedFileMetadata> {

    long countAllFiles(ArchivedFileMetadataSetId id);

    Set<UUID> containsFileMetadataIds(ArchivedFileMetadataSetId id, Collection<UUID> fileMetadataIds);

    default Optional<ArchivedFileMetadata> findByFileMetadataId(
            final ArchivedFileMetadataSetId id,
            final UUID fileMetadataId) {
        return Optional.ofNullable(findByFileMetadataIds(id, Collections.singleton(fileMetadataId)).get(fileMetadataId));
    }

    Map<UUID, ArchivedFileMetadata> findByFileMetadataIds(ArchivedFileMetadataSetId id, Collection<UUID> fileMetadataIds);

    ArchivedFileMetadataSetId intersectWithFileMetadata(ArchivedFileMetadataSetId id, FileMetadataSetId fileMetadataSetId);

    ArchivedFileMetadataSetId filterByBackupIncrements(ArchivedFileMetadataSetId id, SortedSet<Integer> versions);

    Set<String> asEntryPaths(ArchivedFileMetadataSetId id);

    SortedSet<FileMetadata> findFileMetadataByArchiveLocator(
            ArchivedFileMetadataSetId id, FileMetadataSetId files, ArchiveEntryLocator currentLocator);
}
