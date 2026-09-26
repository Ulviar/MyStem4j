package io.github.ulviar.mystem4j;

/**
 * Process communication, response decoding, or JSON-line framing failed.
 *
 * <p>This also reports interruption and smoke-probe validation failures. It does not imply that a
 * morphology parser rejected JSON: the runtime normally returns raw output without parsing it.
 * A reusable session that failed during text execution must be closed and replaced; a pool replaces its
 * failed worker. An interrupted caller keeps its thread interrupt flag.
 */
public class MystemProtocolException extends MystemException {
    /**
     * Creates a failure with its underlying cause.
     *
     * @param message explanation of the failure
     * @param cause underlying failure, or null when unavailable
     */
    public MystemProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
