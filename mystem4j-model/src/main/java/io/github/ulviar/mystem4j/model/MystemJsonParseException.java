package io.github.ulviar.mystem4j.model;

public class MystemJsonParseException extends RuntimeException {
    public MystemJsonParseException(String message) {
        super(message);
    }

    public MystemJsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
