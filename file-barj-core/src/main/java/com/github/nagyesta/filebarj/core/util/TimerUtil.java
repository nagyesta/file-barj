package com.github.nagyesta.filebarj.core.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for calculating elapsed time and rate.
 */
@UtilityClass
public final class TimerUtil {

    private static final BigDecimal SECONDS = new BigDecimal(1000);
    private static final BigDecimal MINUTES = new BigDecimal(60);

    private static final BigDecimal BYTE_TO_MEBIBYTE = new BigDecimal(1024 * 1024);

    /**
     * Calculates and the process summary containing the elapsed time and rate.
     *
     * @param durationMillis       Duration in milliseconds
     * @param totalBackupSizeBytes Total backup size in bytes
     * @return Process summary
     */
    @NonNull
    public static String toProcessSummary(final long durationMillis, final long totalBackupSizeBytes) {
        final var elapsedSeconds = new BigDecimal(durationMillis).setScale(2, RoundingMode.HALF_UP)
                .divide(SECONDS, 2, RoundingMode.HALF_UP);
        final var elapsedMinutes = elapsedSeconds.divide(MINUTES, RoundingMode.HALF_UP);
        final var remainingSeconds = elapsedSeconds.remainder(MINUTES);
        if (elapsedMinutes.setScale(0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) == 0) {
            return remainingSeconds.toPlainString() + " seconds";
        }
        final var rate = new BigDecimal(totalBackupSizeBytes).divide(elapsedMinutes.multiply(BYTE_TO_MEBIBYTE), RoundingMode.HALF_UP);
        return elapsedMinutes.setScale(0, RoundingMode.DOWN).toPlainString() + " minutes "
                + remainingSeconds.toPlainString() + " seconds (speed: " + rate.toPlainString() + " MiB/min)";
    }
}
