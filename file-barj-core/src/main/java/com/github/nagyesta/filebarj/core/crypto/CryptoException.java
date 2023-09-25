package com.github.nagyesta.filebarj.core.crypto;

/**
 * Exception thrown when a crypto operation fails.
 */
public class CryptoException extends RuntimeException {

    /**
     * Creates a new instance and initializes it with the specified message
     * and cause.
     *
     * @param message the message
     * @param cause   the cause
     */
    public CryptoException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
