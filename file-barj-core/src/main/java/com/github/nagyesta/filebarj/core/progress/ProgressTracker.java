package com.github.nagyesta.filebarj.core.progress;

import org.jetbrains.annotations.NotNull;

public interface ProgressTracker extends ObservableProgress {

    void reset();

    void reset(@NotNull ProgressStep step);

    void estimateStepSubtotal(@NotNull ProgressStep step, long totalSubSteps);

    default void recordProgressInSubSteps(final @NotNull ProgressStep step) {
        recordProgressInSubSteps(step, 1L);
    }

    void recordProgressInSubSteps(@NotNull ProgressStep step, long progress);

    void completeStep(ProgressStep step);

    void skipStep(ProgressStep step);

    void completeAll();

    void assertSupports(@NotNull ProgressStep step);
}
