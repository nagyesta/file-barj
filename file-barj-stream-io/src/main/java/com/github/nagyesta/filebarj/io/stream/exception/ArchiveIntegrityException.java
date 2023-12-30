package com.github.nagyesta.filebarj.io.stream.exception;

/**
 * Exception thrown when the backup archive is in an invalid state.
 */
public class ArchiveIntegrityException extends RuntimeException {
    /**
     * Creates a new instance and initializes it with the specified message.
     *
     * @param message the message
     */
    public ArchiveIntegrityException(final String message) {
        super(message);
    }
}
