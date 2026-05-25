package io.github.ulviar.mystem4j.tokenization;

import io.github.ulviar.mystem4j.MystemException;

/**
 * Thrown when parsed MyStem output cannot be converted into offset-safe search tokens.
 */
public class MystemTokenizationException extends MystemException {
    public MystemTokenizationException(String message) {
        super(message);
    }
}
