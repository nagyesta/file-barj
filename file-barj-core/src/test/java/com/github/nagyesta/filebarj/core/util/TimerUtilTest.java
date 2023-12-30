package com.github.nagyesta.filebarj.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TimerUtilTest {

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testToProcessSummaryShouldOmitRateWhenCalledWithADurationShorterThanOneMinute() {
        //given

        //then
        final var actual = TimerUtil.toProcessSummary(1000L, 10L);

        //then
        Assertions.assertEquals("1.00 seconds", actual);
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void testToProcessSummaryShouldContainRateWhenCalledWithADurationLongerThanOneMinute() {
        //given

        //then
        final var actual = TimerUtil.toProcessSummary(61000L, 1024L * 1024L);

        //then
        Assertions.assertEquals("1 minutes 1.00 seconds (speed: 1 MiB/min)", actual);
    }
}
