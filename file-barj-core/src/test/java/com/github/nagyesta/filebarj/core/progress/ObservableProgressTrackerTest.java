package com.github.nagyesta.filebarj.core.progress;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.nagyesta.filebarj.core.progress.ProgressStep.*;
import static org.mockito.Mockito.*;

class ObservableProgressTrackerTest extends TempFileAwareTest {

    private static final long SUB_STEPS_SCAN = 200L;
    private static final long SUB_STEPS_PARSE = 6000L;
    private static final long SUB_STEPS_BACKUP = 500000L;
    private static final int HUNDRED_PERCENT = 100;
    private static final int SCALE = 10;
    private static final BigDecimal TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS = BigDecimal.valueOf(HUNDRED_PERCENT)
            .divide(BigDecimal.valueOf(3), SCALE, RoundingMode.HALF_UP);
    private static final int PARSE_DEFAULT_STEPS = 10;
    private static final int BACKUP_DEFAULT_STEPS = 5;

    @Test
    void testCompleteStepShouldReportHundredPercentAsSubProgressWhenCalled() {
        //given
        final var underTest = createUnderTest(Map.of());
        final var listener = registerListener(underTest);

        //when
        underTest.completeStep(SCAN_FILES);
        underTest.completeStep(PARSE_METADATA);
        underTest.completeStep(BACKUP);

        //then
        final var inOrder = inOrder(listener);
        inOrder.verify(listener).getId();
        var totalProgressCounter = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS;
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, SCAN_FILES.getDisplayName());
        totalProgressCounter = totalProgressCounter.add(TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS);
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, PARSE_METADATA.getDisplayName());
        totalProgressCounter = totalProgressCounter.add(TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS);
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, BACKUP.getDisplayName());
    }

    @Test
    void tesRecordProgressInSubStepsShouldReportAtRegularFrequencyWhenSmallStepsAreMade() {
        //given
        final var underTest = createUnderTest(Map.of());
        final var listener = registerListener(underTest);
        final var scanStepping = SUB_STEPS_SCAN / 100L;
        final var parseStepping = SUB_STEPS_PARSE / 200L;
        final var backupStepping = SUB_STEPS_BACKUP / 200L;

        //when
        for (var i = 0L; i < SUB_STEPS_SCAN; i += scanStepping) {
            underTest.recordProgressInSubSteps(SCAN_FILES);
        }
        underTest.completeStep(SCAN_FILES);
        for (var i = 0L; i < SUB_STEPS_PARSE; i += parseStepping) {
            underTest.recordProgressInSubSteps(PARSE_METADATA, parseStepping);
        }
        underTest.completeStep(PARSE_METADATA);
        for (var i = 0L; i < SUB_STEPS_BACKUP; i += backupStepping) {
            underTest.recordProgressInSubSteps(BACKUP, backupStepping);
        }

        //then
        final var inOrder = inOrder(listener);
        inOrder.verify(listener).getId();
        var totalProgressCounter = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS;
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, SCAN_FILES.getDisplayName());
        final var parseTotalProcessStep = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.TEN, SCALE, RoundingMode.HALF_UP);
        for (var subProgress = PARSE_DEFAULT_STEPS; subProgress <= HUNDRED_PERCENT; subProgress += PARSE_DEFAULT_STEPS) {
            totalProgressCounter = totalProgressCounter.add(parseTotalProcessStep);
            inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), subProgress, PARSE_METADATA.getDisplayName());
        }
        final var backupTotalProcessStep = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.valueOf(20), SCALE, RoundingMode.HALF_UP);
        for (var subProgress = BACKUP_DEFAULT_STEPS; subProgress <= HUNDRED_PERCENT; subProgress += BACKUP_DEFAULT_STEPS) {
            totalProgressCounter = totalProgressCounter.add(backupTotalProcessStep);
            inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), subProgress, BACKUP.getDisplayName());
        }
    }

    @Test
    void tesRecordProgressInSubStepsShouldReportAtRegularFrequencyWhenSmallStepsAreMadeWithUnequalWeights() {
        //given
        final var underTest = createUnderTest(Map.of(PARSE_METADATA, 2, BACKUP, 3));
        final var listener = registerListener(underTest);
        final var scanStepping = SUB_STEPS_SCAN / 100L;
        final var parseStepping = SUB_STEPS_PARSE / 200L;
        final var backupStepping = SUB_STEPS_BACKUP / 200L;

        //when
        for (var i = 0L; i < SUB_STEPS_SCAN; i += scanStepping) {
            underTest.recordProgressInSubSteps(SCAN_FILES);
        }
        underTest.completeStep(SCAN_FILES);
        for (var i = 0L; i < SUB_STEPS_PARSE; i += parseStepping) {
            underTest.recordProgressInSubSteps(PARSE_METADATA, parseStepping);
        }
        underTest.completeStep(PARSE_METADATA);
        for (var i = 0L; i < SUB_STEPS_BACKUP; i += backupStepping) {
            underTest.recordProgressInSubSteps(BACKUP, backupStepping);
        }

        //then
        final var inOrder = inOrder(listener);
        inOrder.verify(listener).getId();
        var totalProgressCounter = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, SCAN_FILES.getDisplayName());
        final var parseTotalProcessStep = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.valueOf(HUNDRED_PERCENT / PARSE_DEFAULT_STEPS), SCALE, RoundingMode.HALF_UP);
        for (var subProgress = PARSE_DEFAULT_STEPS; subProgress <= HUNDRED_PERCENT; subProgress += PARSE_DEFAULT_STEPS) {
            totalProgressCounter = totalProgressCounter.add(parseTotalProcessStep);
            inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), subProgress, PARSE_METADATA.getDisplayName());
        }
        final var backupTotalProcessStep = BigDecimal.valueOf(HUNDRED_PERCENT / 2)
                .divide(BigDecimal.valueOf(HUNDRED_PERCENT / BACKUP_DEFAULT_STEPS), SCALE, RoundingMode.HALF_UP);
        for (var subProgress = BACKUP_DEFAULT_STEPS; subProgress <= HUNDRED_PERCENT; subProgress += BACKUP_DEFAULT_STEPS) {
            totalProgressCounter = totalProgressCounter.add(backupTotalProcessStep);
            inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), subProgress, BACKUP.getDisplayName());
        }
    }

    @Test
    void tesRecordProgressInSubStepsShouldReportWhenABigStepIsTakenLargerThanFrequency() {
        //given
        final var underTest = createUnderTest(Map.of());
        final var listener = registerListener(underTest);
        final var scanStepping = SUB_STEPS_SCAN / 2L;
        final var parseStepping = SUB_STEPS_PARSE / 6L;
        final var backupStepping = SUB_STEPS_BACKUP / 4L;

        //when
        for (var i = 0L; i < SUB_STEPS_SCAN; i += scanStepping) {
            underTest.recordProgressInSubSteps(SCAN_FILES);
        }
        underTest.completeStep(SCAN_FILES);
        for (var i = 0L; i < SUB_STEPS_PARSE; i += parseStepping) {
            underTest.recordProgressInSubSteps(PARSE_METADATA, parseStepping);
        }
        underTest.completeStep(PARSE_METADATA);
        for (var i = 0L; i < SUB_STEPS_BACKUP; i += backupStepping) {
            underTest.recordProgressInSubSteps(BACKUP, backupStepping);
        }

        //then
        final var inOrder = inOrder(listener);
        inOrder.verify(listener).getId();
        var totalProgressCounter = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS;
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, SCAN_FILES.getDisplayName());
        final var parseTotalProcessStep = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.valueOf(6), SCALE, RoundingMode.HALF_UP);
        final var parseSubProcStep = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
        for (var subProgress = parseSubProcStep; toInt(subProgress) <= HUNDRED_PERCENT; subProgress = subProgress.add(parseSubProcStep)) {
            totalProgressCounter = totalProgressCounter.add(parseTotalProcessStep);
            inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), toInt(subProgress), PARSE_METADATA.getDisplayName());
        }
        final var backupTotalProcessStep = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS
                .divide(BigDecimal.valueOf(4), SCALE, RoundingMode.HALF_UP);
        final var backupPercentageSteps = 25;
        for (var subProgress = backupPercentageSteps; subProgress <= HUNDRED_PERCENT; subProgress += backupPercentageSteps) {
            totalProgressCounter = totalProgressCounter.add(backupTotalProcessStep);
            inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), subProgress, BACKUP.getDisplayName());
        }
    }

    @Test
    void testCompleteAllShouldCompleteAllStepsOneByOneWhenCalled() {
        //given
        final var underTest = createUnderTest(Map.of());
        final var listener = registerListener(underTest);

        //when
        underTest.completeAll();

        //then
        final var inOrder = inOrder(listener);
        inOrder.verify(listener).getId();
        var totalProgressCounter = TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS;
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, SCAN_FILES.getDisplayName());
        totalProgressCounter = totalProgressCounter.add(TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS);
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, PARSE_METADATA.getDisplayName());
        totalProgressCounter = totalProgressCounter.add(TOTAL_PROGRESS_PER_STEP_EQUAL_WEIGHTS);
        inOrder.verify(listener).onProgressChanged(toInt(totalProgressCounter), HUNDRED_PERCENT, BACKUP.getDisplayName());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testRegisterListenerShouldThrowExceptionWhenCalledWithNull() {
        //given
        final var underTest = createUnderTest(Map.of());

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> underTest.registerListener(null));
        underTest.completeAll();

        //then + exception
    }

    private static int toInt(final BigDecimal totalProgressCounter) {
        return totalProgressCounter
                .setScale(1, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static ProgressListener registerListener(final ObservableProgressTracker underTest) {
        final var listener = mock(ProgressListener.class);
        when(listener.getId()).thenReturn(UUID.randomUUID());
        underTest.registerListener(listener);
        return listener;
    }

    private static ObservableProgressTracker createUnderTest(final Map<ProgressStep, Integer> weights) {
        final var steps = List.of(SCAN_FILES, PARSE_METADATA, BACKUP);
        final var underTest = new ObservableProgressTracker(steps, steps.stream()
                .collect(Collectors.toMap(Function.identity(), step -> weights.getOrDefault(step, 1))));
        underTest.estimateStepSubtotal(SCAN_FILES, SUB_STEPS_SCAN);
        underTest.estimateStepSubtotal(PARSE_METADATA, SUB_STEPS_PARSE);
        underTest.estimateStepSubtotal(BACKUP, SUB_STEPS_BACKUP);
        return underTest;
    }
}
