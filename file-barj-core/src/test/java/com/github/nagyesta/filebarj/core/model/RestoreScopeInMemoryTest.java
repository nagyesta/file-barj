package com.github.nagyesta.filebarj.core.model;

import com.github.nagyesta.filebarj.core.persistence.DataStore;

public class RestoreScopeInMemoryTest extends RestoreScopeTest {

    @Override
    protected DataStore getDataStore() {
        return DataStore.newInMemoryInstance();
    }
}
