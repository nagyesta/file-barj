package com.github.nagyesta.filebarj.core.backup;

/**
 * Indicates a failure which happened during file parsing.
 */
public class FileParseException extends RuntimeException {
    /**
     * Creates a new instance and initializes it with the specified cause.
     *
     * @param cause   the cause
     */
    public FileParseException(final Throwable cause) {
        super(cause);
    }
}
