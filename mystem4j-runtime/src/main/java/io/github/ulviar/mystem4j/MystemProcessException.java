package io.github.ulviar.mystem4j;

import java.util.OptionalInt;

/**
 * A MyStem process exited unsuccessfully; exit status and bounded diagnostics may be available.
 */
public class MystemProcessException extends MystemException {
    /** Process exit status when available. */
    private final OptionalInt exitCode;
    /** Bounded diagnostic text, stored as empty rather than null. */
    private final String stderr;

    /**
     * Creates a process failure with available diagnostics.
     *
     * @param message explanation of the failure
     * @param exitCode process exit status, or an empty value when unavailable
     * @param stderr captured diagnostic text; null is stored as an empty string
     */
    public MystemProcessException(String message, OptionalInt exitCode, String stderr) {
        super(message);
        this.exitCode = exitCode;
        this.stderr = stderr == null ? "" : stderr;
    }

    /**
     * Creates a process failure with available diagnostics.
     *
     * @param message explanation of the failure
     * @param exitCode process exit status, or an empty value when unavailable
     * @param stderr captured diagnostic text; null is stored as an empty string
     * @param cause underlying failure, or null when unavailable
     */
    public MystemProcessException(String message, OptionalInt exitCode, String stderr, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.stderr = stderr == null ? "" : stderr;
    }

    /**
     * Returns the exit status supplied with this failure.
     *
     * @return exit status, or an empty value when the process status was unavailable
     */
    public OptionalInt exitCode() {
        return exitCode;
    }

    /**
     * Returns captured diagnostic output.
     *
     * <p>This text is bounded and may be truncated. Session failures may include protocol diagnostics
     * rather than only the stderr stream.
     *
     * @return diagnostic text, or an empty string when unavailable
     */
    public String stderr() {
        return stderr;
    }
}
