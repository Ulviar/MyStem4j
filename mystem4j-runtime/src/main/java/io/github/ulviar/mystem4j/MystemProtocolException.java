package io.github.ulviar.mystem4j;

/**
 * Process communication, response decoding, or JSON-line framing failed.
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
