package io.github.ulviar.mystem4j;

/**
 * Base unchecked exception for validation, native MyStem execution and remote-transport failures.
 *
 * <p>Catch a specific subtype when recovery depends on the cause: invalid input, request timeout, pool
 * saturation, and process failure require different handling. Built-in clients preserve the caller's
 * interrupt flag when an interrupted operation is reported through this hierarchy.
 *
 * <p>Messages are diagnostic text, not a stable machine-readable format. Use exception types and, for
 * process exits, {@link MystemProcessException#exitCode()} instead of parsing messages.
 * Builder argument errors such as null values or non-positive limits may use the standard
 * {@link NullPointerException} or {@link IllegalArgumentException} instead of this hierarchy.
 *
 * @see MystemClient#analyze(String)
 */
public class MystemException extends RuntimeException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure, or null when unavailable
     */
    public MystemException(String message) {
        super(message);
    }

    /**
     * Creates a failure with its underlying cause.
     *
     * @param message explanation of the failure, or null when unavailable
     * @param cause underlying failure, or null when unavailable
     */
    public MystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
