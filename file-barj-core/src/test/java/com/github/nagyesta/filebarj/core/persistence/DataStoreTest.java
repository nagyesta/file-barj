package com.github.nagyesta.filebarj.core.persistence;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
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
                .add(Arguments.of(DataStore.newEmbeddedSqlInstance(), filePath, false))
                .add(Arguments.of(DataStore.newEmbeddedSqlInstance(), archived, false))
                .add(Arguments.of(DataStore.newEmbeddedSqlInstance(), fileMetadata, false))
                .add(Arguments.of(DataStore.newEmbeddedSqlInstance(), change, false))
                .add(Arguments.of(DataStore.newEmbeddedSqlInstance(), noop, true))
                .add(Arguments.of(DataStore.newInMemoryInstance(), filePath, false))
                .add(Arguments.of(DataStore.newInMemoryInstance(), archived, false))
                .add(Arguments.of(DataStore.newInMemoryInstance(), fileMetadata, false))
                .add(Arguments.of(DataStore.newInMemoryInstance(), change, false))
                .add(Arguments.of(DataStore.newInMemoryInstance(), noop, true))
                .build();
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testNewDefaultInstanceShouldReturnAnEmptyDataStoreWhenCalled(final DataStore notEmpty) {
        //given
        try (notEmpty) {
            notEmpty.filePathSetRepository().createFileSet();

            //when
            try (var underTest = DataStore.newDefaultInstance()) {

                //then
                assertFalse(notEmpty.areAllRepositoriesClosed());
                assertTrue(underTest.areAllRepositoriesClosed());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testSingleThreadedPoolShouldReturnAThreadPoolWithOneThreadWhenTheDataStoreIsNotClosed(final DataStore underTest) {
        //given
        try (underTest) {

            //when
            final var threadPool = underTest.singleThreadedPool();

            //then
            assertNotNull(threadPool);
            assertEquals(1, threadPool.getParallelism());
            assertFalse(threadPool.isShutdown());
        }
    }

    @ParameterizedTest
    @MethodSource("com.github.nagyesta.filebarj.core.test.DataStoreProvider#dataStoreSupplierProvider")
    void testSingleThreadedPoolShouldReturnAClosedThreadPoolWhenTheDataStoreIsClosed(final DataStore underTest) {
        //given
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
            final DataStore underTest,
            final Consumer<DataStore> modification,
            final boolean isEmpty) {
        //given
        try (underTest) {
            modification.accept(underTest);

            //when
            final var actual = underTest.areAllRepositoriesClosed();

            //then
            assertEquals(isEmpty, actual);
        }
    }
}
