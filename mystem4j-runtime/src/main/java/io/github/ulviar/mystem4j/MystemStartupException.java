package io.github.ulviar.mystem4j;

/**
 * A reusable MyStem session or worker pool could not be started.
 */
public class MystemStartupException extends MystemException {
    /**
     * Creates a failure with its underlying cause.
     *
     * @param message explanation of the failure
     * @param cause underlying failure, or null when unavailable
     */
    public MystemStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
