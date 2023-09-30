package com.github.nagyesta.filebarj.core.backup;

/**
 * Exception thrown when the backup (as in the archival itself) process fails.
 */
public class ArchivalException extends RuntimeException {
    /**
     * Creates a new instance and initializes it with the specified message.
     * @param message the message
     */
    public ArchivalException(final String message) {
        super(message);
    }

    /**
     * Creates a new instance and initializes it with the specified message and cause.
     *
     * @param message the message
     * @param cause   the cause
     */
    public ArchivalException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
