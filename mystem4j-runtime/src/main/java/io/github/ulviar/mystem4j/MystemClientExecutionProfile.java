package io.github.ulviar.mystem4j;

/**
 * Describes the process and concurrency profile of a MyStem client implementation.
 *
 * <p>The profile describes text-request processing. All built-in profiles use separate one-shot processes
 * for file requests. To identify how a particular completed request ran, inspect
 * {@link MystemRequestStats#mode()} instead.
 *
 * @see MystemClient#executionProfile()
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
