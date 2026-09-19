package io.github.ulviar.mystem4j.model;

/**
 * Indicates invalid JSON syntax, an unsupported MyStem JSON shape, or a wrong type for a known field.
 *
 * <p>Parser-generated messages include the JSON line and column when available. Alignment failures
 * instead produce {@link MystemTextIssueType#UNMATCHED_TOKEN} issues on a successfully parsed document.
 */
public class MystemJsonParseException extends RuntimeException {
    /**
     * Creates a parse failure with a diagnostic message.
     *
     * @param message diagnostic message, optionally including a JSON location
     */
    public MystemJsonParseException(String message) {
        super(message);
    }

    /**
     * Creates a parse failure retaining its underlying cause.
     *
     * @param message diagnostic message, optionally including a JSON location
     * @param cause underlying parser or input failure
     */
    public MystemJsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
