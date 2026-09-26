package io.github.ulviar.mystem4j.tokenization;

/**
 * Thrown when parsed MyStem output cannot be converted into offset-safe search tokens.
 */
public class MystemTokenizationException extends RuntimeException {
    /**
     * Creates a failure describing an invalid source range or a rejected unaligned token.
     *
     * @param message diagnostic describing the tokenization failure; may be {@code null}
     */
    public MystemTokenizationException(String message) {
        super(message);
    }
}
