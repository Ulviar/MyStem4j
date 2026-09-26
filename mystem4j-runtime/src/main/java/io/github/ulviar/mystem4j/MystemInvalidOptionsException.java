package io.github.ulviar.mystem4j;

/**
 * Options, request payload limits, JSON-line framing, or file arguments are invalid.
 *
 * <p>For built-in clients, oversized text and CR/LF in session/pool requests are rejected before execution.
 * Correcting that input permits a later request on the same client. Option combinations are checked when
 * options or clients are built; request and file arguments are checked when submitted.
 *
 * @see MystemOptions.Builder#build()
 * @see MystemClientBuilder#build()
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
