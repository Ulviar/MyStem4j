package io.github.ulviar.mystem4j;

/**
 * A request encountered a closed client or underlying process session.
 *
 * <p>An explicitly closed client cannot be reopened. Create a new client before submitting more requests.
 * A closed reusable session also requires a replacement client. When only a pooled worker has closed,
 * the pool retires that worker and can accept later requests; the whole pool need not be replaced.
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
