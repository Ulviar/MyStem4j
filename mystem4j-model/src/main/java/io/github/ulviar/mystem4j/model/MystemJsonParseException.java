package io.github.ulviar.mystem4j.model;

/**
 * Indicates invalid JSON syntax, an unsupported MyStem JSON shape, or a wrong type for a known field.
 *
 * <p>Parser-generated messages include the JSON line and column when available. Alignment failures
 * instead produce {@link MystemTextIssueType#UNMATCHED_TOKEN} issues on a successfully parsed document.
 * A failed parse does not return a partial document. The message is a human-readable diagnostic,
 * not a stable format for extracting fields or source locations.
 *
 * @see MystemJsonParser
 */
public class MystemJsonParseException extends RuntimeException {
    /**
     * Creates a parse failure with a diagnostic message.
     *
     * @param message diagnostic message, optionally including a JSON location; may be {@code null}
     */
    public MystemJsonParseException(String message) {
        super(message);
    }

    /**
     * Creates a parse failure retaining its underlying cause.
     *
     * @param message diagnostic message, optionally including a JSON location; may be {@code null}
     * @param cause underlying parser or input failure, or {@code null} when no cause is available
     */
    public MystemJsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
