package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.persistence.DataStore;

public class HashingFileMetadataChangeDetectorInMemoryIntegrationTest
        extends HashingFileMetadataChangeDetectorIntegrationTest {

    @Override
    protected DataStore getDataStore() {
        return DataStore.newInMemoryInstance();
    }
}
