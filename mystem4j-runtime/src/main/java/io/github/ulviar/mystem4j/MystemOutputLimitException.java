package io.github.ulviar.mystem4j;

/**
 * Captured output or a session output backlog exceeded its configured bound.
 */
public class MystemOutputLimitException extends MystemException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public MystemOutputLimitException(String message) {
        super(message);
    }
}
