package io.github.ulviar.mystem4j;

/**
 * A pooled request could not obtain admission or a worker within the acquisition timeout.
 */
public class MystemPoolExhaustedException extends MystemException {
    /**
     * Creates a failure with its underlying cause.
     *
     * @param message explanation of the failure
     * @param cause underlying failure, or null when unavailable
     */
    public MystemPoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
