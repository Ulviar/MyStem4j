package io.github.ulviar.mystem4j;

/**
 * Describes the process and concurrency profile of a MyStem client implementation.
 */
public enum MystemClientExecutionProfile {
    /**
     * The client implementation does not expose its process model.
     */
    UNKNOWN,

    /**
     * Each text request starts a separate MyStem process.
     */
    ONE_SHOT_PROCESS_PER_REQUEST,

    /**
     * Text requests share one reusable MyStem process and are serialized by the client.
     */
    REUSABLE_SESSION,

    /**
     * Text requests are served by a bounded pool of reusable MyStem processes.
     */
    POOLED_SESSIONS
}
