package io.github.ulviar.mystem4j;

/**
 * No configured or discoverable path identifies a regular executable MyStem file.
 */
public class MystemExecutableNotFoundException extends MystemException {
    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public MystemExecutableNotFoundException(String message) {
        super(message);
    }
}
