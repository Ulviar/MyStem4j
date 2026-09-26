package io.github.ulviar.mystem4j.lucene;

/**
 * Controls whether skipped source tokens create gaps for phrase and proximity queries.
 *
 * <p>Only the first form of a search token advances the position; its other forms are synonyms at
 * position increment zero. Skipped tokens are counted as tokens, not individual characters.
 */
public enum MystemLucenePositionPolicy {
    /**
     * Separators and other skipped tokens do not add position gaps.
     */
    COMPACT,

    /**
     * Each skipped separator or other token increments the next search token position.
     *
     * <p>Leading gaps affect the first emitted token; trailing gaps are included when the stream ends.
     */
    PRESERVE_SKIPPED_TOKENS
}
