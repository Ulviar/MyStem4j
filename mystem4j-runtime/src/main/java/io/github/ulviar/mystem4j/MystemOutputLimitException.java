package io.github.ulviar.mystem4j;

/**
 * Captured output or a session stdout backlog exceeded its configured bound.
 *
 * <p>No partial result is returned. Review the input and configured response limits before retrying;
 * excessive stderr can trigger this exception for one-shot requests even when stdout is small.
 * Session and pool text requests discard excess stderr without failing. A reusable session must be
 * closed and replaced after a stdout-limit failure; pools discard the affected worker. A failed file
 * request uses its own one-shot process and does not invalidate the reusable text session.
 *
 * @see MystemClientBuilder#maxResponseChars(int)
 * @see MystemClientBuilder#maxResponseBytes(int)
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
