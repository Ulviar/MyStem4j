package io.github.ulviar.mystem4j;

/**
 * A request was submitted after the client was closed.
 */
public class MystemClosedException extends MystemException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public MystemClosedException(String message) {
        super(message);
    }
}
