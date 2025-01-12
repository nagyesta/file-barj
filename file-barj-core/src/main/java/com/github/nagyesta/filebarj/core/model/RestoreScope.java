package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.common.ManifestDatabase;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.Data;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the scope of the restore process in case of a backup increment.
 */
@Data
public class RestoreScope {

    private final Map<ArchiveEntryLocator, SortedSet<FileMetadata>> contentSourcesInScopeByLocator;

    private final Map<BackupPath, FileMetadata> changedContentSourcesByPath;

    private final Set<BackupPath> allKnownPathsInBackup;

    /**
     * Creates a new instance and preprocesses the restore scope.
     *
     * @param manifestDatabase            The manifest database containing the file and archive metadata
     * @param selectedIncrement           The increment we want to restore
     *                                    increment
     * @param fileStatusesLatestIncrement The change statuses compared the files from the
     *                                    latest backup increment
     * @param restoreScope                The selected file paths we should restore
     */
    public RestoreScope(
            final ManifestDatabase manifestDatabase,
            final ManifestId selectedIncrement,
            final Map<BackupPath, Change> fileStatusesLatestIncrement,
            final Set<BackupPath> restoreScope) {
        final var fileIdsInContentRestoreScope = allArchiveEntriesLatestIncrement.values().stream()
                .map(ArchivedFileMetadata::getFiles)
                .flatMap(Collection::stream)
                //file is in the restore scope
                .filter(fileId -> restoreScope.contains(allFilesLatestIncrement.get(fileId).getAbsolutePath()))
                //the file content must be restored
                .filter(fileId -> {
                    final var fileMetadata = allFilesLatestIncrement.get(fileId);
                    //if the change status is missing, that means the file was put out of scope earlier
                    return fileStatusesLatestIncrement
                            .getOrDefault(fileMetadata.getAbsolutePath(), Change.NO_CHANGE)
                            .isRestoreContent()
                            //always keep the symbolic links in scope as their change status may not be accurate
                            //when restoring links referencing other files from the backup scope, and we are
                            //restoring the content to a new location instead of the original source directory
                            || fileMetadata.getFileType() == FileType.SYMBOLIC_LINK;
                })
                .collect(Collectors.toSet());
        this.allKnownPathsInBackup = Stream.concat(
                        //all files in the archive
                        allArchiveEntriesLatestIncrement.values().stream()
                                .map(ArchivedFileMetadata::getFiles)
                                .flatMap(Collection::stream)
                                .map(allFilesLatestIncrement::get)
                                .map(FileMetadata::getAbsolutePath),
                        //all directories
                        allFilesLatestIncrement.values().stream()
                                .filter(file -> file.getFileType() == FileType.DIRECTORY)
                                .map(FileMetadata::getAbsolutePath))
                .collect(Collectors.toUnmodifiableSet());
        this.changedContentSourcesByPath = allFilesLatestIncrement.values().stream()
                //keep only content sources
                .filter(file -> file.getFileType() != FileType.DIRECTORY)
                //keep only content sources that have changed
                //if the change status is missing, that means the file was put out of scope earlier
                .filter(file -> fileStatusesLatestIncrement
                        .getOrDefault(file.getAbsolutePath(), Change.NO_CHANGE)
                        .isRestoreContent())
                .collect(Collectors.toUnmodifiableMap(FileMetadata::getAbsolutePath, Function.identity()));
        this.contentSourcesInScopeByLocator = allArchiveEntriesLatestIncrement.values().stream()
                //keep only content sources in the restore scope
                .filter(entry -> entry.getFiles().stream().anyMatch(fileIdsInContentRestoreScope::contains))
                //must merge the different archive metadata entries, because they can have the same
                //locator in case of incremental backups
                .collect(Collectors.groupingBy(ArchivedFileMetadata::getArchiveLocation))
                .entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> new TreeSet<>(entry.getValue().stream()
                                .map(ArchivedFileMetadata::getFiles)
                                .flatMap(Collection::stream)
                                //keep only files in the restore scope
                                .filter(fileIdsInContentRestoreScope::contains)
                                .map(allFilesLatestIncrement::get)
                                .collect(Collectors.toSet()))));
    }
}
