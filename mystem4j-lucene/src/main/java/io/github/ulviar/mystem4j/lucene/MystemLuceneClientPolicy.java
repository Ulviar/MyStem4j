package io.github.ulviar.mystem4j.lucene;

/**
 * Controls construction-time warnings or rejection for known MyStem execution profiles.
 *
 * <p>This policy does not change client concurrency or make an unknown custom client thread-safe.
 * JSON output is required under every policy.
 */
public enum MystemLuceneClientPolicy {
    /**
     * Accept every execution profile without a performance warning.
     */
    ALLOW_ANY,

    /**
     * Log a warning for one-shot clients and reusable single-process sessions.
     *
     * <p>These clients are accepted, but one-shot clients create a process per request and sessions
     * serialize requests through one process. Pooled and unknown profiles do not cause a warning.
     */
    WARN_ON_KNOWN_SLOW_CLIENTS,

    /**
     * Reject one-shot and single-session profiles with {@link IllegalArgumentException}.
     * Pooled clients and custom clients with an unknown profile are accepted.
     */
    REQUIRE_POOLED_OR_UNKNOWN
}
