package com.github.nagyesta.filebarj.core.progress;

import org.jetbrains.annotations.NotNull;

public class NoOpProgressTracker implements ProgressTracker {

    /**
     * The shared instance of the No-Op tracker.
     */
    public static final NoOpProgressTracker INSTANCE = new NoOpProgressTracker();

    @Override
    public void reset() {
        //no-op
    }

    @Override
    public void reset(final @NotNull ProgressStep step) {
        //no-op
    }

    @Override
    public void estimateStepSubtotal(final @NotNull ProgressStep step, final long totalSubSteps) {
        //no-op
    }

    @Override
    public void recordProgressInSubSteps(final @NotNull ProgressStep step, final long progress) {
        //no-op
    }

    @Override
    public void completeStep(final @NotNull ProgressStep step) {
        //no-op
    }

    @Override
    public void skipStep(final @NotNull ProgressStep step) {
        //no-op
    }

    @Override
    public void completeAll() {
        //no-op
    }

    @Override
    public void assertSupports(final @NotNull ProgressStep step) {
        //no-op
    }

    @Override
    public void registerListener(final @NotNull ProgressListener listener) {

    }
}
