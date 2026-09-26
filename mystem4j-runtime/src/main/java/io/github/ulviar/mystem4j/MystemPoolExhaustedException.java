package io.github.ulviar.mystem4j;

/**
 * A request could not obtain admission or processing capacity.
 *
 * <p>Native pools report admission or worker-acquisition timeouts through this type. An HTTP service
 * can also reject excess concurrent requests immediately, even with a one-shot or session backend.
 * This is a capacity/acquisition failure, distinct from {@link MystemRequestTimeoutException}, which
 * bounds execution after a worker has been acquired. The failed acquisition does not close the client.
 *
 * @see MystemPoolOptions#acquireTimeout()
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
