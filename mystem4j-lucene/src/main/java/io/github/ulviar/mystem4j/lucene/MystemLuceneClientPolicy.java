package io.github.ulviar.mystem4j.lucene;

/**
 * Controls how Lucene integration treats known MyStem client execution profiles.
 */
public enum MystemLuceneClientPolicy {
    /**
     * Do not check the supplied MyStem client profile.
     */
    ALLOW_ANY,

    /**
     * Log a warning when the supplied runtime client is known to be expensive or serialized.
     */
    WARN_ON_KNOWN_SLOW_CLIENTS,

    /**
     * Reject known one-shot and single-session runtime clients. Custom clients with an unknown profile are accepted.
     */
    REQUIRE_POOLED_OR_UNKNOWN
}
