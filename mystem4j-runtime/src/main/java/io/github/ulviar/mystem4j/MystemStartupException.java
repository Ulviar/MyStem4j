package io.github.ulviar.mystem4j;

/**
 * A MyStem process or pooled worker could not be started.
 *
 * <p>This can occur during client construction for a reusable session or pool warmup, or during a request
 * for one-shot execution or lazy pool worker creation. Check the underlying cause for operating-system
 * launch errors; resolving an executable path alone does not prove it can run on this machine.
 *
 * @see MystemProbe
 */
public class MystemStartupException extends MystemException {
    /**
     * Creates a failure with its underlying cause.
     *
     * @param message explanation of the failure
     * @param cause underlying failure, or null when unavailable
     */
    public MystemStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
