package io.github.ulviar.mystem4j.lucene;

/**
 * Controls whether skipped source tokens create gaps for phrase and proximity queries.
 *
 * <p>Only the first form of a search token advances the position; its other forms are synonyms at
 * position increment zero. Skipped tokens are counted as tokens, not individual characters.
 * Search tokens whose forms all exceed Lucene's term-byte limit occupy one position under both
 * policies, including trailing positions reported by {@link MystemLuceneTokenizer#end()}.
 */
public enum MystemLucenePositionPolicy {
    /**
     * Tokens classified as separators or other non-search fragments do not add position gaps.
     */
    COMPACT,

    /**
     * Each skipped separator or other token increments the next search token position.
     *
     * <p>Leading gaps affect the first emitted token; trailing gaps are included when the stream ends.
     */
    PRESERVE_SKIPPED_TOKENS
}
