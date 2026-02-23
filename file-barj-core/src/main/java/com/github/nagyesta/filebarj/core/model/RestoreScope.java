package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import lombok.Data;
import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;

/**
 * Represents the scope of the restore process in case of a backup increment.
 */
@Data
public class RestoreScope implements Closeable {

    private final DataStore dataStore;

    private final FileMetadataSetId filesFromLastIncrementFilteredByContentRestoreScope;

    private final ArchivedFileMetadataSetId archivedFilesFromLastIncrementFilteredByContentRestoreScope;

    private final FileMetadataSetId changedContentSources;

    private final FileMetadataSetId allFilesLatestIncrement;

    /**
     * Creates a new instance and preprocesses the restore scope.
     *
     * @param dataStore                        The DataStore containing the working data
     * @param allFilesLatestIncrement          All known files from the latest backup increment
     * @param allArchiveEntriesLatestIncrement All known archive entries from the latest backup
     *                                         increment
     * @param fileStatusesLatestIncrement      The change statuses compared the files from the
     *                                         latest backup increment
     * @param restoreScope                     The files we want to restore
     */
    public RestoreScope(
            final DataStore dataStore,
            final FileMetadataSetId allFilesLatestIncrement,
            final ArchivedFileMetadataSetId allArchiveEntriesLatestIncrement,
            final BackupPathChangeStatusMapId fileStatusesLatestIncrement,
            final FileMetadataSetId restoreScope) {
        this.dataStore = dataStore;
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore.archivedFileMetadataSetRepository();
        this.allFilesLatestIncrement = allFilesLatestIncrement;
        this.filesFromLastIncrementFilteredByContentRestoreScope = fileMetadataSetRepository
                .intersectByPath(allFilesLatestIncrement, restoreScope);
        this.archivedFilesFromLastIncrementFilteredByContentRestoreScope = archivedFileMetadataSetRepository
                .intersectWithFileMetadata(allArchiveEntriesLatestIncrement, restoreScope);
        this.changedContentSources = fileMetadataSetRepository
                .keepChangedContent(filesFromLastIncrementFilteredByContentRestoreScope, fileStatusesLatestIncrement);
    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(filesFromLastIncrementFilteredByContentRestoreScope);
        IOUtils.closeQuietly(archivedFilesFromLastIncrementFilteredByContentRestoreScope);
        IOUtils.closeQuietly(changedContentSources);
    }
}
