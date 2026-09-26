package io.github.ulviar.mystem4j;

/**
 * MyStem did not complete a request within its configured execution timeout.
 *
 * <p>No partial result is returned. The affected execution is terminated. After a text-request timeout,
 * a reusable session must be closed and replaced, while a pool discards the failed worker. A failed file
 * request does not invalidate a reusable text session, but direct file output may leave a partial file
 * under caller ownership.
 *
 * <p>The timeout excludes session serialization waits and pool admission/acquisition. It is not a bound
 * on the complete wall-clock duration of an {@link MystemClient#analyze(String)} call.
 *
 * @see MystemClientBuilder#requestTimeout(java.time.Duration)
 * @see MystemPoolExhaustedException
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
