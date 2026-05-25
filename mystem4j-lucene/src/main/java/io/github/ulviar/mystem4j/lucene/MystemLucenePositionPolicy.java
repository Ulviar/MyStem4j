package io.github.ulviar.mystem4j.lucene;

/**
 * Controls how skipped non-search tokens affect Lucene token positions.
 */
public enum MystemLucenePositionPolicy {
    /**
     * Separators and other skipped tokens do not add position gaps.
     */
    COMPACT,

    /**
     * Each skipped separator or other token increments the next search token position.
     */
    PRESERVE_SKIPPED_TOKENS
}
