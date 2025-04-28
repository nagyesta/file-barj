package com.github.nagyesta.filebarj.io.stream.crypto;

/**
 * Exception thrown when a crypto operation fails.
 */
public class CryptoException extends RuntimeException {

    /**
     * Creates a new instance and initializes it with the specified message and cause.
     *
     * @param message the message
     */
    public CryptoException(final String message) {
        super(message);
    }

    /**
     * Creates a new instance and initializes it with the specified message and cause.
     *
     * @param message the message
     * @param cause   the cause
     */
    public CryptoException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
