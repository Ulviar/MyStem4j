package io.github.ulviar.mystem4j;

/**
 * MyStem did not complete a request within its configured execution timeout.
 */
public class MystemRequestTimeoutException extends MystemException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public MystemRequestTimeoutException(String message) {
        super(message);
    }
}
