package io.github.ulviar.mystem4j;

public class MystemException extends RuntimeException {
    public MystemException(String message) {
        super(message);
    }

    public MystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
