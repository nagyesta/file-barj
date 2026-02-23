package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DataStoreTest extends TempFileAwareTest {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Stream<Arguments> dataStoreContentSource() {
        final Consumer<DataStore> filePath = (final DataStore dataStore) -> dataStore.filePathSetRepository().createFileSet();
        final Consumer<DataStore> archived = (final DataStore dataStore) -> dataStore.archivedFileMetadataSetRepository().createFileSet();
        final Consumer<DataStore> fileMetadata = (final DataStore dataStore) -> dataStore.fileMetadataSetRepository().createFileSet();
        final Consumer<DataStore> change = (final DataStore dataStore) -> dataStore.backupPathChangeStatusMapRepository().createFileMap();
        final Consumer<DataStore> noop = DataStore::filePathSetRepository;
        return Stream.<Arguments>builder()
                .add(Arguments.of(filePath, false))
                .add(Arguments.of(archived, false))
                .add(Arguments.of(fileMetadata, false))
                .add(Arguments.of(change, false))
                .add(Arguments.of(noop, true))
                .build();
    }

    @Test
    void testNewInMemoryInstanceShouldReturnAnEmptyDataStoreWhenCalled() {
        //given
        try (var notEmpty = DataStore.newInMemoryInstance()) {
            notEmpty.filePathSetRepository().createFileSet();

            //when
            try (var underTest = DataStore.newInMemoryInstance()) {

                //then
                assertFalse(notEmpty.areAllRepositoriesClosed());
                assertTrue(underTest.areAllRepositoriesClosed());
            }
        }
    }

    @Test
    void testSingleThreadedPoolShouldReturnAThreadPoolWithOneThreadWhenTheDataStoreIsNotClosed() {
        //given
        try (var underTest = DataStore.newInMemoryInstance()) {

            //when
            final var threadPool = underTest.singleThreadedPool();

            //then
            assertNotNull(threadPool);
            assertEquals(1, threadPool.getParallelism());
            assertFalse(threadPool.isShutdown());
        }
    }

    @Test
    void testSingleThreadedPoolShouldReturnAClosedThreadPoolWhenTheDataStoreIsClosed() {
        //given
        final var underTest = DataStore.newInMemoryInstance();
        underTest.close();

        //when
        final var threadPool = underTest.singleThreadedPool();

        //then
        assertNotNull(threadPool);
        assertEquals(1, threadPool.getParallelism());
        assertTrue(threadPool.isShutdown());
    }

    @ParameterizedTest
    @MethodSource("dataStoreContentSource")
    void testAreAllRepositoriesClosedShouldReturnTrueWhenNoFileSetsArePresent(
            final Consumer<DataStore> modification,
            final boolean isEmpty) {
        //given
        try (var underTest = DataStore.newInMemoryInstance()) {
            modification.accept(underTest);

            //when
            final var actual = underTest.areAllRepositoriesClosed();

            //then
            assertEquals(isEmpty, actual);
        }
    }
}
