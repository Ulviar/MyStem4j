package io.github.ulviar.mystem4j;

/**
 * Base unchecked exception for executable resolution, validation and MyStem execution failures.
 */
public class MystemException extends RuntimeException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public MystemException(String message) {
        super(message);
    }

    /**
     * Creates a failure with its underlying cause.
     *
     * @param message explanation of the failure
     * @param cause underlying failure, or null when unavailable
     */
    public MystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
