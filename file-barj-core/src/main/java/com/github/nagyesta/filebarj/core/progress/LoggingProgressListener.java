package com.github.nagyesta.filebarj.core.progress;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j(topic = "progress")
@SuppressWarnings("java:S6548")
public class LoggingProgressListener implements ProgressListener {

    /**
     * The shared instance of the Logging progress listener.
     */
    public static final LoggingProgressListener INSTANCE = new LoggingProgressListener();

    @Override
    public UUID getId() {
        return UUID.randomUUID();
    }

    @Override
    public void onProgressChanged(
            final int totalProgressPercentage,
            final int stepProgressPercentage,
            final String stepName) {
        log.info("({}%) {} step {}% complete.",
                String.format("%3d", totalProgressPercentage), stepName, stepProgressPercentage);
    }
}
