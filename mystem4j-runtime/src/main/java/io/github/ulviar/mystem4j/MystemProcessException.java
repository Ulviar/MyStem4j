package io.github.ulviar.mystem4j;

import java.util.OptionalInt;

public class MystemProcessException extends MystemException {
    private final OptionalInt exitCode;
    private final String stderr;

    public MystemProcessException(String message, OptionalInt exitCode, String stderr) {
        super(message);
        this.exitCode = exitCode;
        this.stderr = stderr == null ? "" : stderr;
    }

    public MystemProcessException(String message, OptionalInt exitCode, String stderr, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.stderr = stderr == null ? "" : stderr;
    }

    public OptionalInt exitCode() {
        return exitCode;
    }

    public String stderr() {
        return stderr;
    }
}
