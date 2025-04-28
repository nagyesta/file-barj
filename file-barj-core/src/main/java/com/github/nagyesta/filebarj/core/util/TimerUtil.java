package com.github.nagyesta.filebarj.core.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

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
    public static @NotNull String toProcessSummary(
            final long durationMillis,
            final long totalBackupSizeBytes) {
        final var elapsedSeconds = toElapsedSeconds(durationMillis);
        final var elapsedMinutes = toElapsedMinutes(elapsedSeconds);
        final var remainingSeconds = getRemainderSecondsOfIncompleteMinutes(elapsedSeconds);
        return minutesToString(elapsedMinutes) + secondsToString(remainingSeconds)
                + rateToString(totalBackupSizeBytes, elapsedMinutes);
    }

    /**
     * Calculates and the process summary containing the elapsed time.
     *
     * @param durationMillis Duration in milliseconds
     * @return Process summary
     */
    public static @NotNull String toProcessSummary(final long durationMillis) {
        return toProcessSummary(durationMillis, 0L);
    }

    private static @NotNull String rateToString(
            final long totalBackupSizeBytes,
            final BigDecimal elapsedMinutes) {
        if (totalBackupSizeBytes == 0 || isZero(elapsedMinutes)) {
            return "";
        }
        final var rate = new BigDecimal(totalBackupSizeBytes).divide(elapsedMinutes.multiply(BYTE_TO_MEBIBYTE), RoundingMode.HALF_UP);
        return " (speed: " + rate.toPlainString() + " MiB/min)";
    }

    private static @NotNull String secondsToString(final BigDecimal remainingSeconds) {
        return remainingSeconds.toPlainString() + " seconds";
    }

    private static @NotNull String minutesToString(final BigDecimal elapsedMinutes) {
        if (isZero(elapsedMinutes)) {
            return "";
        }
        return elapsedMinutes.setScale(0, RoundingMode.DOWN).toPlainString() + " minutes ";
    }

    private static boolean isZero(final BigDecimal elapsedMinutes) {
        return elapsedMinutes.setScale(0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) == 0;
    }

    private static @NotNull BigDecimal getRemainderSecondsOfIncompleteMinutes(final BigDecimal elapsedSeconds) {
        return elapsedSeconds.remainder(MINUTES);
    }

    private static @NotNull BigDecimal toElapsedMinutes(final BigDecimal elapsedSeconds) {
        return elapsedSeconds.divide(MINUTES, RoundingMode.HALF_UP);
    }

    private static @NotNull BigDecimal toElapsedSeconds(final long durationMillis) {
        return new BigDecimal(durationMillis).setScale(2, RoundingMode.HALF_UP)
                .divide(SECONDS, 2, RoundingMode.HALF_UP);
    }
}
