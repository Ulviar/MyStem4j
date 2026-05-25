package io.github.ulviar.mystem4j.tokenization;

/**
 * Thrown when parsed MyStem output cannot be converted into offset-safe search tokens.
 */
public class MystemTokenizationException extends RuntimeException {
    public MystemTokenizationException(String message) {
        super(message);
    }
}
