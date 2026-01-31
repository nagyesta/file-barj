package com.github.nagyesta.filebarj.core.common;

import java.io.Closeable;
import java.util.concurrent.locks.ReentrantLock;

public class SingleUseController implements Closeable {

    private final ReentrantLock executionLock = new ReentrantLock();
    private boolean executed;

    public SingleUseController() {
        executed = false;
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
        executionLock.unlock();
    }
}
