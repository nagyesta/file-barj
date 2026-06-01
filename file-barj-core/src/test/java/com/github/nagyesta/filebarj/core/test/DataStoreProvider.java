package com.github.nagyesta.filebarj.core.test;

import com.github.nagyesta.filebarj.core.persistence.DataStore;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public class DataStoreProvider {
    public static Stream<Arguments> dataStoreSupplierProvider() {
        return Stream.of(
                Arguments.of(DataStore.newEmbeddedSqlInstance()),
                Arguments.of(DataStore.newInMemoryInstance())
        );
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static Stream<Arguments> dataStoreSupplierProviderWithMultiThreads() {
        return Stream.of(
                Arguments.of(1, DataStore.newEmbeddedSqlInstance()),
                Arguments.of(3, DataStore.newEmbeddedSqlInstance()),
                Arguments.of(1, DataStore.newInMemoryInstance()),
                Arguments.of(3, DataStore.newInMemoryInstance())
        );
    }
}
