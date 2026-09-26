package io.github.ulviar.mystem4j;

/**
 * No configured or discoverable path identifies a regular executable MyStem file.
 *
 * <p>Check the explicit path or the selected system-property/environment value, and whether the file is
 * executable. Resolution does not test binary architecture or run the program; an executable that cannot
 * start may instead produce {@link MystemStartupException}.
 *
 * @see MystemClientBuilder#executable(java.nio.file.Path)
 * @see MystemClientBuilder#searchPath(boolean)
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
