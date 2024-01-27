package com.github.nagyesta.filebarj.job.cli;

import lombok.Getter;

/**
 * Represents the different tasks that can be executed.
 */
@Getter
public enum Task {

    /**
     * Creating a backup.
     */
    BACKUP("backup"),
    /**
     * Restoring the contents of a backup.
     */
    RESTORE("restore"),
    /**
     * Generating a key pair for the encryption.
     */
    GEN_KEYS("gen-keys"),
    /**
     * Listing the increments of a backup.
     */
    INSPECT_INCREMENTS("inspect-increments"),
    /**
     * Listing the contents of a backup increment.
     */
    INSPECT_CONTENT("inspect-content");

    private final String command;

    /**
     * Initializes a constant.
     *
     * @param command the name of the command
     */
    Task(final String command) {
        this.command = command;
    }
}
