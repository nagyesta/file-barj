package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParser;
import com.github.nagyesta.filebarj.core.backup.worker.FileMetadataParserFactory;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private HashMap<UUID, FileMetadata> origFiles;
    @SuppressWarnings("FieldCanBeLocal")
    private HashMap<UUID, ArchivedFileMetadata> origArchived;
    private List<Path> origScope;
    private HashMap<UUID, FileMetadata> incFiles;
    private HashMap<UUID, ArchivedFileMetadata> incArchived;
    private List<Path> incScope;

    @SuppressWarnings("DataFlowIssue")
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
        origFiles = new HashMap<>();
        origArchived = new HashMap<>();
        origScope = List.of(dir, dir2, file1, file2, file3, link1, link2, link3);
        populateFileAndArchiveMapsWithForFullBackup(origScope, origFiles, origArchived);
        setFileContent(DIR_FILE_1_TXT, "some changed content 1");
        file4 = setFileContent(DIR_FILE_4_TXT, "some new content 4");
        setFileContent(DIR_FILE_3_TXT, null);
        setSymbolicLink(DIR_LINK_1_TXT, null);
        setSymbolicLink(DIR_LINK_2_TXT, file1);
        setSymbolicLink(DIR_LINK_3_TXT, null);
        setFileContent(DIR_LINK_3_TXT, "new type");
        link4 = setSymbolicLink(DIR_LINK_4_TXT, file4);
        incFiles = new HashMap<>();
        incArchived = new HashMap<>();
        incScope = List.of(dir, file1, file2, file4, link2, link3, link4);
        populateFileAndArchiveMapsWithForIncrementalBackup(incScope, origFiles, origArchived, incFiles, incArchived);
    }

    @Test
    void testGetContentSourcesInScopeByLocatorShouldNotFilterArchiveEntriesWhenScopeIsFullAndEverythingIsMissing() {
        //given
        final var changes = markEverythingAsDeleted(incFiles);
        final var scope = new HashSet<>(incScope);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getContentSourcesInScopeByLocator();

        //then
        final var actualPaths = actual.values().stream()
                .map(SortedSet::first)
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toCollection(TreeSet::new));
        final var expectedPaths = new TreeSet<>(incScope.subList(1, incScope.size()));
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    @Test
    void testGetContentSourcesInScopeByLocatorShouldFilterArchiveEntriesWhenScopeIsFullButSomeFilesAreInExpectedState() {
        //given
        final var changes = markOriginalFilesAsChangedAndNewFilesAsDeleted(incFiles, origScope);
        final var scope = new HashSet<>(incScope);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getContentSourcesInScopeByLocator();

        //then
        final var actualPaths = actual.values().stream()
                .map(SortedSet::first)
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toCollection(TreeSet::new));
        //add link2 too as symbolic links are always kept in scope
        final var expectedPaths = new TreeSet<>(Set.of(file1, file2, link2, link3, link4, file4));
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    @Test
    void testGetContentSourcesInScopeByLocatorShouldFilterArchiveEntriesWhenScopeIsNotFullAndAllFilesAreMissing() {
        //given
        final var changes = markEverythingAsDeleted(incFiles);
        final var scope = Set.of(dir, file1, file4, link4);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getContentSourcesInScopeByLocator();

        //then
        final var actualPaths = actual.values().stream()
                .map(SortedSet::first)
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toCollection(TreeSet::new));
        final var expectedPaths = new TreeSet<>(Set.of(file1, link4, file4));
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    @Test
    void testGetChangedContentSourcesByPathShouldNotFilterArchiveEntriesWhenScopeIsFullAndEverythingIsMissing() {
        //given
        final var changes = markEverythingAsDeleted(incFiles);
        final var scope = new HashSet<>(incScope);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getChangedContentSourcesByPath();

        //then
        final var actualPaths = new TreeSet<>(actual.keySet());
        final var expectedPaths = new TreeSet<>(incScope.subList(1, incScope.size()));
        Assertions.assertEquals(expectedPaths, actualPaths);
        actual.forEach((path, fileMetadata) -> Assertions.assertEquals(path, fileMetadata.getAbsolutePath()));
    }

    @Test
    void testGetChangedContentSourcesByPathShouldFilterArchiveEntriesWhenScopeIsFullAndButSomeFilesAreInExpectedState() {
        //given
        final var changes = markOriginalFilesAsChangedAndNewFilesAsDeleted(incFiles, origScope);
        final var scope = new HashSet<>(incScope);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getChangedContentSourcesByPath();

        //then
        final var actualPaths = new TreeSet<>(actual.keySet());
        final var expectedPaths = new TreeSet<>(Set.of(file1, file2, link3, link4, file4));
        Assertions.assertEquals(expectedPaths, actualPaths);
        actual.forEach((path, fileMetadata) -> Assertions.assertEquals(path, fileMetadata.getAbsolutePath()));
    }

    @Test
    void testGetChangedContentSourcesByPathShouldNotFilterArchiveEntriesWhenScopeIsNotFullAndEverythingIsMissing() {
        //given
        final var changes = markEverythingAsDeleted(incFiles);
        final var scope = Set.of(dir, file1, file4, link4);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getChangedContentSourcesByPath();

        //then
        final var actualPaths = new TreeSet<>(actual.keySet());
        final var expectedPaths = new TreeSet<>(incScope.subList(1, incScope.size()));
        Assertions.assertEquals(expectedPaths, actualPaths);
        actual.forEach((path, fileMetadata) -> Assertions.assertEquals(path, fileMetadata.getAbsolutePath()));
    }

    @Test
    void testGetAllKnownPathsInBackupShouldNotFilterFilesWhenScopeIsNotFullAndSomeFilesAreInExpectedState() {
        //given
        final var changes = markOriginalFilesAsChangedAndNewFilesAsDeleted(incFiles, origScope);
        final var scope = Set.of(dir, file1, file4, link4);
        final var underTest = new RestoreScope(incFiles, incArchived, changes, scope);

        //when
        final var actual = underTest.getAllKnownPathsInBackup();

        //then
        final var actualPaths = new TreeSet<>(actual);
        final var expectedPaths = new TreeSet<>(incScope);
        Assertions.assertEquals(expectedPaths, actualPaths);
    }

    private static Map<Path, Change> markEverythingAsDeleted(
            final Map<UUID, FileMetadata> incFiles) {
        return incFiles.values().stream()
                .map(FileMetadata::getAbsolutePath)
                .collect(Collectors.toMap(Function.identity(), k -> Change.DELETED));
    }

    private static Map<Path, Change> markOriginalFilesAsChangedAndNewFilesAsDeleted(
            final Map<UUID, FileMetadata> incFiles,
            final List<Path> origScope) {
        final var changes = new HashMap<Path, Change>();
        incFiles.forEach((id, file) -> {
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
        return changes;
    }

    private void populateFileAndArchiveMapsWithForIncrementalBackup(
            final List<Path> files,
            final Map<UUID, FileMetadata> origFiles,
            final Map<UUID, ArchivedFileMetadata> origArchived,
            final Map<UUID, FileMetadata> incFiles,
            final Map<UUID, ArchivedFileMetadata> incArchived) {
        files.stream()
                .map(path -> fileMetadataParser.parse(path.toFile(), config))
                .forEach(file -> {
                    final var path = file.getAbsolutePath();
                    if (!file.getFileType().isContentSource()) {
                        incFiles.put(file.getId(), file);
                    } else if (origFiles.values().stream().noneMatch(f -> f.getAbsolutePath().equals(path))) {
                        incFiles.put(file.getId(), file);
                        generateNewArchiveMetadataAndAddToMap(incArchived, file, 1);
                    } else {
                        origFiles.values().stream()
                                .filter(f -> f.getAbsolutePath().equals(path))
                                .map(FileMetadata::getArchiveMetadataId)
                                .map(origArchived::get)
                                .map(ArchivedFileMetadata::copyArchiveDetails)
                                .forEach(copied -> {
                                    file.setArchiveMetadataId(copied.getId());
                                    copied.getFiles().add(file.getId());
                                    incArchived.put(copied.getId(), copied);
                                });
                        incFiles.put(file.getId(), file);
                    }
                });
    }

    private void populateFileAndArchiveMapsWithForFullBackup(
            final List<Path> files,
            final Map<UUID, FileMetadata> origFiles,
            final Map<UUID, ArchivedFileMetadata> origArchived) {
        files.stream()
                .map(path -> fileMetadataParser.parse(path.toFile(), config))
                .forEach(file -> {
                    origFiles.put(file.getId(), file);
                    generateNewArchiveMetadataAndAddToMap(origArchived, file, 0);
                });
    }

    private static void generateNewArchiveMetadataAndAddToMap(
            final Map<UUID, ArchivedFileMetadata> archivedMap,
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
            archivedMap.put(archivedId, archived);
            file.setArchiveMetadataId(archivedId);
        }
    }

    private Path createDirectory(final String name) throws IOException {
        final var path = testDataRoot.resolve(name);
        Files.createDirectory(path);
        return path;
    }

    private Path setFileContent(final String name, final String content) throws IOException {
        if (content == null) {
            Files.deleteIfExists(testDataRoot.resolve(name));
            return null;
        }
        final var path = testDataRoot.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private Path setSymbolicLink(final String name, final Path target) throws IOException {
        Files.deleteIfExists(testDataRoot.resolve(name));
        if (target == null) {
            return null;
        }
        final var path = testDataRoot.resolve(name);
        Files.createSymbolicLink(path, target);
        return path;
    }
}
