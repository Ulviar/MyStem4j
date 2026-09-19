package io.github.ulviar.mystem4j;

/**
 * Options, request payload limits, JSON-line framing, or file arguments are invalid.
 */
public class MystemInvalidOptionsException extends MystemException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public MystemInvalidOptionsException(String message) {
        super(message);
    }
}
