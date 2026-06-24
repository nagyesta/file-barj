package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.persistence.DataStore;

class BackupIncrementManifestInMemoryTest extends BackupIncrementManifestTest {

    @Override
    protected DataStore getDataStore() {
        return DataStore.newInMemoryInstance();
    }
}
