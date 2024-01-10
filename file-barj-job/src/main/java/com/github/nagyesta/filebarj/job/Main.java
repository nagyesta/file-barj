package com.github.nagyesta.filebarj.job;

import lombok.extern.slf4j.Slf4j;

/**
 * Main class for the backup job.
 */
@SuppressWarnings({"checkstyle:HideUtilityClassConstructor"})
@Slf4j
public class Main {

    /**
     * Entry point for the backup job.
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        new Main().execute(args);
    }

    void execute(final String[] args) {
        try {
            final var controller = new Controller(args, System.console());
            controller.run();
        } catch (final Exception e) {
            log.error("Execution failed: ", e);
            exitWithError();
        }
    }

    void exitWithError() {
        System.exit(1);
    }

}
