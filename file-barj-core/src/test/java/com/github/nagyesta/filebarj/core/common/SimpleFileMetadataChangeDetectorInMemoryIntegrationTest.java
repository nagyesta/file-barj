package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.persistence.DataStore;

public class SimpleFileMetadataChangeDetectorInMemoryIntegrationTest
        extends SimpleFileMetadataChangeDetectorIntegrationTest {

    @Override
    protected DataStore getDataStore() {
        return DataStore.newInMemoryInstance();
    }
}
