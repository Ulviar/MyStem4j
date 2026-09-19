package io.github.ulviar.mystem4j;

/**
 * Execution path of one completed request, as reported by {@link MystemRequestStats}.
 */
public enum MystemExecutionMode {
    /** A separate process handling caller-supplied text. */
    ONE_SHOT_TEXT,
    /** A separate process handling file arguments, regardless of the client mode. */
    ONE_SHOT_FILE,
    /** A text request handled by one reusable JSON-line process. */
    SESSION,
    /** A text request handled by a worker in a pool of JSON-line processes. */
    POOL
}
