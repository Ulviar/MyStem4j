package io.github.ulviar.mystem4j.model;

import io.github.ulviar.mystem4j.MystemException;

public class MystemJsonParseException extends MystemException {
    public MystemJsonParseException(String message) {
        super(message);
    }

    public MystemJsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
