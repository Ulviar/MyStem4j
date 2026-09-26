package io.github.ulviar.mystem4j;

/**
 * A request exceeded the deadline configured by its client implementation.
 *
 * <p>Native runtime clients return no partial result and terminate the affected execution. After a text-request timeout,
 * a reusable session must be closed and replaced, while a pool discards the failed worker. A failed file
 * request does not invalidate a reusable text session, but direct file output may leave a partial file
 * under caller ownership.
 *
 * <p>Remote transports can instead bound the complete HTTP exchange. An HTTP timeout does not prove
 * that remote processing stopped; consult the transport client's cancellation contract.
 *
 * <p>The native execution timeout excludes session serialization waits and pool admission/acquisition. It is not a bound
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
