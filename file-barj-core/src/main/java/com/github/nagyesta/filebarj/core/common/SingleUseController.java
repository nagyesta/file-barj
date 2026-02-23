package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.persistence.DataStore;

import java.io.Closeable;
import java.util.concurrent.locks.ReentrantLock;

public class SingleUseController implements Closeable {

    private final ReentrantLock executionLock = new ReentrantLock();
    private final DataStore dataStore;
    private boolean executed;

    protected SingleUseController(final DataStore dataStore) {
        this.dataStore = dataStore;
        executed = false;
    }

    protected DataStore dataStore() {
        return dataStore;
    }

    protected SingleUseController lock() {
        checkExecutable();
        executionLock.lock();
        checkExecutable();
        executed = true;
        return this;
    }

    private void checkExecutable() {
        if (executed) {
            throw new IllegalStateException("This controller is already executed. Please re-create it before another execution.");
        }
    }

    @Override
    public void close() {
        dataStore.close();
        if (executionLock.isLocked()) {
            executionLock.unlock();
        }
    }
}
