package com.github.nagyesta.filebarj.core.progress;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ObservableProgressTracker implements ProgressTracker {

    private static final double HUNDRED_PERCENT_DOUBLE = 100.0D;
    private static final int COMPLETE = 100;
    private static final int NO_PROGRESS = 0;
    private static final long SINGLE_STEP = 1L;

    private final List<ProgressStep> steps;
    private final Map<ProgressStep, AtomicLong> subStepTotals;
    private final Map<UUID, ProgressListener> listeners = new ConcurrentHashMap<>();
    private final Map<ProgressStep, AtomicLong> completedSubSteps;
    private final Map<ProgressStep, Integer> weights;
    private final Map<ProgressStep, AtomicInteger> lastReportedSubStepPercentage;
    private final ReentrantLock lock = new ReentrantLock();

    public ObservableProgressTracker(final List<ProgressStep> steps) {
        this(steps, steps.stream().collect(Collectors.toMap(Function.identity(), ProgressStep::getDefaultWeight)));
    }

    public ObservableProgressTracker(
            final List<ProgressStep> steps,
            final Map<ProgressStep, Integer> weights) {
        this.steps = List.copyOf(steps);
        this.weights = Map.copyOf(weights);
        this.subStepTotals = steps.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), step -> new AtomicLong(SINGLE_STEP)));
        this.completedSubSteps = steps.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), step -> new AtomicLong(NO_PROGRESS)));
        this.lastReportedSubStepPercentage = steps.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), step -> new AtomicInteger(NO_PROGRESS)));
        reset();
    }

    @Override
    public void reset() {
        this.steps.forEach(this::reset);
    }

    @Override
    public void reset(final @NotNull ProgressStep step) {
        assertSupports(step);
        this.subStepTotals.get(step).set(SINGLE_STEP);
        this.completedSubSteps.get(step).set(NO_PROGRESS);
        this.lastReportedSubStepPercentage.get(step).set(NO_PROGRESS);
    }

    @Override
    public void estimateStepSubtotal(
            final @NotNull ProgressStep step,
            final long totalSubSteps) {
        assertSupports(step);
        subStepTotals.get(step).set(totalSubSteps);
    }

    @Override
    public void recordProgressInSubSteps(
            final @NotNull ProgressStep step,
            final long progress) {
        assertSupports(step);
        if (progress <= 0) {
            throw new IllegalArgumentException("The progress must be greater than zero.");
        }
        completedSubSteps.get(step).addAndGet(progress);
        report(step);
    }

    @Override
    public void completeStep(final @NotNull ProgressStep step) {
        assertSupports(step);
        completedSubSteps.get(step).set(subStepTotals.get(step).get());
        report(step);
    }

    @Override
    public void skipStep(final @NotNull ProgressStep step) {
        assertSupports(step);
        lastReportedSubStepPercentage.get(step).set(NO_PROGRESS);
        completedSubSteps.get(step).set(subStepTotals.get(step).get());
    }

    @Override
    public void completeAll() {
        steps.forEach(this::completeStep);
    }

    @Override
    public void registerListener(final @NonNull ProgressListener listener) {
        listeners.putIfAbsent(listener.getId(), listener);
    }

    @Override
    public void assertSupports(final @NonNull ProgressStep step) {
        if (!supports(step)) {
            throw new IllegalStateException("The " + step.getDisplayName() + " step is not supported.");
        }
    }

    private boolean supports(final @NotNull ProgressStep step) {
        return steps.contains(step);
    }

    private int calculateTotalProgress() {
        final var totalWeights = weights.values().stream().mapToLong(i -> i * COMPLETE).sum();
        final var totalWeightedProgress = steps.stream()
                .mapToLong(step -> ((long) calculateProgress(step)) * weights.get(step))
                .sum();
        return calculatePercentage(totalWeights, totalWeightedProgress);
    }

    private void report(final @NotNull ProgressStep step) {
        final var subProcessPercentage = calculateProgress(step);
        if (lastReportedSubStepPercentage.get(step).get() == subProcessPercentage) {
            return;
        }
        try {
            lock.lock();
            if (lastReportedSubStepPercentage.get(step).get() == subProcessPercentage) {
                return;
            }
            if (notComplete(subProcessPercentage)
                    && notOnExactReportFrequency(step, subProcessPercentage)
                    && progressIsTooLowSinceLastReport(step, subProcessPercentage)) {
                return;
            }
            lastReportedSubStepPercentage.get(step).set(subProcessPercentage);
        } finally {
            lock.unlock();
        }
        final var totalProcess = calculateTotalProgress();
        listeners.forEach((id, listener) -> listener.onProgressChanged(totalProcess, subProcessPercentage, step.getDisplayName()));
    }

    private boolean progressIsTooLowSinceLastReport(
            final @NotNull ProgressStep step,
            final int subProcessPercentage) {
        return lastReportedSubStepPercentage.get(step).get() + step.getReportFrequencyPercent() > subProcessPercentage;
    }

    private boolean notOnExactReportFrequency(
            final @NotNull ProgressStep step,
            final int subProcessPercentage) {
        return subProcessPercentage % step.getReportFrequencyPercent() > 0;
    }

    private boolean notComplete(final int subProcessPercentage) {
        return subProcessPercentage != COMPLETE;
    }

    private int calculateProgress(final @NotNull ProgressStep step) {
        final var total = subStepTotals.get(step).get();
        final var done = completedSubSteps.get(step).get();
        return calculatePercentage(total, done);
    }

    private int calculatePercentage(
            final long total,
            final long done) {
        final var percentage = (done * HUNDRED_PERCENT_DOUBLE) / total;
        return normalize(percentage);
    }

    private int normalize(final double percentage) {
        return Math.max(NO_PROGRESS, Math.min(COMPLETE, (int) Math.round(percentage)));
    }

}
