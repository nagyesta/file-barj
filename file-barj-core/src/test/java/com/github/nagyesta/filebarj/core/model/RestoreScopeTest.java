package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.ArchivedFileMetadataSetId;
import com.github.nagyesta.filebarj.core.persistence.entities.BackupPathChangeStatusMapId;
import com.github.nagyesta.filebarj.core.persistence.entities.FileMetadataSetId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestoreScopeTest extends TempFileAwareTest {

    private static final String DIR = "dir";
    private static final String DIR_2 = "dir2";
    private static final String DIR_FILE_1_TXT = "dir/file1.txt";
    private static final String DIR_FILE_2_TXT = "dir/file2.txt";
    private static final String DIR_FILE_3_TXT = "dir/file3.txt";
    private static final String DIR_LINK_1_TXT = "dir/link1.txt";
    private static final String DIR_LINK_2_TXT = "dir/link2.txt";
    private static final String DIR_LINK_3_TXT = "dir/link3.txt";
    private static final String DIR_FILE_4_TXT = "dir/file4.txt";
    private static final String DIR_LINK_4_TXT = "dir/link4.txt";
    private final FileMetadataParser fileMetadataParser = FileMetadataParserFactory.newInstance();
    private final BackupJobConfiguration config = mock(BackupJobConfiguration.class);
    private DataStore dataStore;
    private Path dir;
    @SuppressWarnings("FieldCanBeLocal")
    private Path dir2;
    private Path file1;
    private Path file2;
    @SuppressWarnings("FieldCanBeLocal")
    private Path file3;
    private Path file4;
    @SuppressWarnings("FieldCanBeLocal")
    private Path link1;
    @SuppressWarnings("FieldCanBeLocal")
    private Path link2;
    private Path link3;
    private Path link4;
    @SuppressWarnings("FieldCanBeLocal")
    private FileMetadataSetId origFiles;
    private List<BackupPath> origPaths;
    @SuppressWarnings("FieldCanBeLocal")
    private ArchivedFileMetadataSetId origArchived;
    private FileMetadataSetId incFiles;
    private List<FileMetadata> incList;
    private List<BackupPath> incPaths;
    private ArchivedFileMetadataSetId incArchived;
    private FileMetadataSetId incScope;

    @Override
    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        when(config.getHashAlgorithm()).thenReturn(HashAlgorithm.SHA256);
        dir = createDirectory(DIR);
        dir2 = createDirectory(DIR_2);
        file1 = setFileContent(DIR_FILE_1_TXT, "some content 1");
        file2 = setFileContent(DIR_FILE_2_TXT, "some content 2");
        file3 = setFileContent(DIR_FILE_3_TXT, "some content 3");
        link1 = setSymbolicLink(DIR_LINK_1_TXT, file1);
        link2 = setSymbolicLink(DIR_LINK_2_TXT, file2);
        link3 = setSymbolicLink(DIR_LINK_3_TXT, file3);
        dataStore = DataStore.newInMemoryInstance();
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var archivedFileMetadataSetRepository = dataStore.archivedFileMetadataSetRepository();
        origFiles = fileMetadataSetRepository.createFileSet();
        origArchived = archivedFileMetadataSetRepository.createFileSet();
        final var origScope = fileMetadataSetRepository.createFileSet();
        final var origList = Stream.of(dir, dir2, file1, file2, file3, link1, link2, link3)
                .map(path -> fileMetadataParser.parse(path.toFile(), config))
                .toList();
        origPaths = origList.stream()
                .map(FileMetadata::getAbsolutePath)
                .toList();
        fileMetadataSetRepository.appendTo(origScope, origList);
        populateFileAndArchiveMapsWithForFullBackup(origPaths, origFiles, origArchived);
        setFileContent(DIR_FILE_1_TXT, "some changed content 1");
        file4 = setFileContent(DIR_FILE_4_TXT, "some new content 4");
        setFileContent(DIR_FILE_3_TXT, null);
        setSymbolicLink(DIR_LINK_1_TXT, null);
        setSymbolicLink(DIR_LINK_2_TXT, file1);
        setSymbolicLink(DIR_LINK_3_TXT, null);
        setFileContent(DIR_LINK_3_TXT, "new type");
        link4 = setSymbolicLink(DIR_LINK_4_TXT, file4);
        incFiles = fileMetadataSetRepository.createFileSet();
        incArchived = archivedFileMetadataSetRepository.createFileSet();
        incScope = fileMetadataSetRepository.createFileSet();
        incPaths = Stream.of(dir, file1, file2, file4, link2, link3, link4)
                .map(path -> fileMetadataParser.parse(path.toFile(), config))
                .map(FileMetadata::getAbsolutePath)
                .toList();
        populateFileAndArchiveMapsWithForIncrementalBackup(incPaths, origFiles, origArchived, incFiles, incArchived);
        incList = dataStore.fileMetadataSetRepository()
                .findAll(incFiles, 0, Integer.MAX_VALUE)
                .stream()
                .filter(file -> incPaths.contains(file.getAbsolutePath()))
                .toList();
        fileMetadataSetRepository.appendTo(incScope, incList);
    }

    @Test
    void testGetChangedContentSourcesByPathShouldNotFilterArchiveEntriesWhenScopeIsFullAndEverythingIsMissing() {
        //given
        final var changes = markEverythingAsDeleted(incFiles);
        final var underTest = new RestoreScope(dataStore, incFiles, incArchived, changes, incScope);

        //when
        final var actual = underTest.getChangedContentSources();

        //then
        final var actualPaths = toPathSet(actual);
        final var expectedPaths = new TreeSet<>(incPaths.subList(1, incPaths.size()));
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    @Test
    void testGetChangedContentSourcesByPathShouldFilterArchiveEntriesWhenScopeIsFullAndButSomeFilesAreInExpectedState() {
        //given
        final var changes = markOriginalFilesAsChangedAndNewFilesAsDeleted(incFiles, origPaths);
        final var underTest = new RestoreScope(dataStore, incFiles, incArchived, changes, incScope);

        //when
        final var actual = underTest.getChangedContentSources();

        //then
        final var actualPaths = toPathSet(actual);
        final var expectedPaths = Stream.of(file1, file2, link3, link4, file4)
                .map(BackupPath::of)
                .collect(Collectors.toCollection(TreeSet::new));
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    @Test
    void testGetChangedContentSourcesByPathShouldNotFilterArchiveEntriesWhenScopeIsNotFullAndEverythingIsMissing() {
        //given
        final var changes = markEverythingAsDeleted(incFiles);
        final var included = Stream.of(dir, file1, file4, link4).map(BackupPath::of).collect(Collectors.toSet());
        final var filtered = filterByPath(incList, included);
        final var underTest = new RestoreScope(dataStore, incFiles, incArchived, changes, filtered);

        //when
        final var actual = underTest.getChangedContentSources();

        //then
        final var actualPaths = toPathSet(actual);
        final var filteredPaths = List.copyOf(new TreeSet<>(toPathSet(filtered)));
        //remove the directory
        final var expectedPaths = new TreeSet<>(filteredPaths.subList(1, filteredPaths.size()));
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    @Test
    void testGetAllKnownPathsInBackupShouldNotFilterFilesWhenScopeIsNotFullAndSomeFilesAreInExpectedState() {
        //given
        final var changes = markOriginalFilesAsChangedAndNewFilesAsDeleted(incFiles, origPaths);
        final var included = Stream.of(dir, file1, file4, link4).map(BackupPath::of).collect(Collectors.toSet());
        final var filtered = filterByPath(incList, included);
        final var underTest = new RestoreScope(dataStore, incFiles, incArchived, changes, filtered);

        //when
        final var actual = underTest.getAllFilesLatestIncrement();

        //then
        assertContentEquals(incScope, actual);
    }

    private TreeSet<BackupPath> toPathSet(final FileMetadataSetId actual) {
        return dataStore.fileMetadataSetRepository()
                .findAll(actual, 0, Integer.MAX_VALUE)
                .stream()
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private void assertContentEquals(
            final FileMetadataSetId expected,
            final FileMetadataSetId actual) {
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var actualPaths = fileMetadataSetRepository.findAll(actual, 0, Integer.MAX_VALUE)
                .stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath))
                .map(this::fileMetadataCore)
                .toList();
        final var expectedPaths = fileMetadataSetRepository.findAll(expected, 0, Integer.MAX_VALUE)
                .stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath))
                .map(this::fileMetadataCore)
                .toList();
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    private String fileMetadataCore(final FileMetadata fileMetadata) {
        return fileMetadata.getAbsolutePath()
                + "__" + fileMetadata.getFileType()
                + "__" + fileMetadata.getOriginalSizeBytes()
                + "__" + fileMetadata.getLastModifiedUtcEpochSeconds()
                + "__" + fileMetadata.getArchiveMetadataId();
    }

    private FileMetadataSetId filterByPath(
            final List<FileMetadata> fileMetadataList,
            final Set<BackupPath> included) {
        final var scope = fileMetadataList.stream()
                .filter(o -> included.contains(o.getAbsolutePath()))
                .collect(Collectors.toSet());
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var filtered = fileMetadataSetRepository.createFileSet();
        fileMetadataSetRepository.appendTo(filtered, scope);
        return filtered;
    }

    private BackupPathChangeStatusMapId markEverythingAsDeleted(final FileMetadataSetId incFiles) {
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var backupPathChangeStatusMapRepository = dataStore.backupPathChangeStatusMapRepository();
        final var fileMap = backupPathChangeStatusMapRepository.createFileMap();
        fileMetadataSetRepository.forEach(incFiles, dataStore.singleThreadedPool(), file ->
                backupPathChangeStatusMapRepository.appendTo(fileMap, file.getAbsolutePath(), Change.DELETED));
        return fileMap;
    }

    private BackupPathChangeStatusMapId markOriginalFilesAsChangedAndNewFilesAsDeleted(
            final FileMetadataSetId incFiles,
            final List<BackupPath> origScope) {
        final var changes = new HashMap<BackupPath, Change>();
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var backupPathChangeStatusMapRepository = dataStore.backupPathChangeStatusMapRepository();
        fileMetadataSetRepository.forEach(incFiles, dataStore.singleThreadedPool(), file -> {
            if (origScope.contains(file.getAbsolutePath())) {
                if (file.getFileType() == FileType.REGULAR_FILE) {
                    changes.put(file.getAbsolutePath(), Change.CONTENT_CHANGED);
                } else {
                    changes.put(file.getAbsolutePath(), Change.NO_CHANGE);
                }
            } else {
                changes.put(file.getAbsolutePath(), Change.DELETED);
            }
        });
        final var fileMap = backupPathChangeStatusMapRepository.createFileMap();
        backupPathChangeStatusMapRepository.appendTo(fileMap, changes);
        return fileMap;
    }

    private void populateFileAndArchiveMapsWithForIncrementalBackup(
            final List<BackupPath> files,
            final FileMetadataSetId origFileId,
            final ArchivedFileMetadataSetId origArchiveId,
            final FileMetadataSetId incFiles,
            final ArchivedFileMetadataSetId incArchived) {
        final var origFileMap = dataStore.fileMetadataSetRepository()
                .findAll(origFileId, 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(FileMetadata::getId, Function.identity()));
        final var origArchiveMap = dataStore.archivedFileMetadataSetRepository()
                .findAll(origArchiveId, 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(ArchivedFileMetadata::getId, Function.identity()));
        files.stream()
                .map(path -> fileMetadataParser.parse(path.toFile(), config))
                .forEach(file -> {
                    final var path = file.getAbsolutePath();
                    if (!file.getFileType().isContentSource()) {
                        dataStore.fileMetadataSetRepository().appendTo(incFiles, file);
                    } else if (origFileMap.values().stream().noneMatch(f -> f.getAbsolutePath().equals(path))) {
                        dataStore.fileMetadataSetRepository().appendTo(incFiles, file);
                        generateNewArchiveMetadataAndAddToMap(incArchived, file, 1);
                    } else {
                        origFileMap.values().stream()
                                .filter(f -> f.getAbsolutePath().equals(path))
                                .map(FileMetadata::getArchiveMetadataId)
                                .map(origArchiveMap::get)
                                .map(ArchivedFileMetadata::copyArchiveDetails)
                                .forEach(copied -> {
                                    file.setArchiveMetadataId(copied.getId());
                                    copied.getFiles().add(file.getId());
                                    dataStore.archivedFileMetadataSetRepository().appendTo(incArchived, copied);
                                });
                        dataStore.fileMetadataSetRepository().appendTo(incFiles, file);
                    }
                });
    }

    private void populateFileAndArchiveMapsWithForFullBackup(
            final List<BackupPath> files,
            final FileMetadataSetId origFiles,
            final ArchivedFileMetadataSetId origArchived) {
        files.stream()
                .map(path -> fileMetadataParser.parse(path.toFile(), config))
                .forEach(file -> {
                    dataStore.fileMetadataSetRepository().appendTo(origFiles, file);
                    generateNewArchiveMetadataAndAddToMap(origArchived, file, 0);
                });
    }

    private void generateNewArchiveMetadataAndAddToMap(
            final ArchivedFileMetadataSetId archivedSetId,
            final FileMetadata file,
            final int increment) {
        if (file.getFileType().isContentSource()) {
            final var archivedId = UUID.randomUUID();
            final var archived = ArchivedFileMetadata.builder()
                    .id(archivedId)
                    .archiveLocation(ArchiveEntryLocator.builder()
                            .entryName(archivedId)
                            .backupIncrement(increment)
                            .build())
                    .originalHash(file.getOriginalHash())
                    .archivedHash(file.getOriginalHash())
                    .files(Set.of(file.getId()))
                    .build();
            dataStore.archivedFileMetadataSetRepository().appendTo(archivedSetId, archived);
            file.setArchiveMetadataId(archivedId);
        }
    }

    private Path createDirectory(final String name) throws IOException {
        final var path = testDataRoot.resolve(name);
        Files.createDirectory(path);
        return path;
    }

    private Path setFileContent(
            final String name,
            final String content) throws IOException {
        if (content == null) {
            Files.deleteIfExists(testDataRoot.resolve(name));
            return null;
        }
        final var path = testDataRoot.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private Path setSymbolicLink(
            final String name,
            final Path target) throws IOException {
        Files.deleteIfExists(testDataRoot.resolve(name));
        if (target == null) {
            return null;
        }
        final var path = testDataRoot.resolve(name);
        Files.createSymbolicLink(path, target);
        return path;
    }
}
