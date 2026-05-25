package io.github.ulviar.mystem4j.lucene;

import java.util.Objects;

/**
 * Controls Lucene-side MyStem analysis limits and token position behavior.
 *
 * @param maxInputChars maximum number of UTF-16 code units read from one Lucene field
 * @param maxChunkChars maximum number of UTF-16 code units sent to MyStem in one request
 * @param positionPolicy policy for Lucene position increments across skipped tokens
 */
public record MystemLuceneAnalysisOptions(
        int maxInputChars, int maxChunkChars, MystemLucenePositionPolicy positionPolicy) {
    public static final int DEFAULT_MAX_INPUT_CHARS = 1_000_000;
    public static final int DEFAULT_MAX_CHUNK_CHARS = 32_768;

    public MystemLuceneAnalysisOptions {
        if (maxInputChars <= 0) {
            throw new IllegalArgumentException("maxInputChars must be positive");
        }
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("maxChunkChars must be positive");
        }
        if (maxChunkChars > maxInputChars) {
            throw new IllegalArgumentException("maxChunkChars must not exceed maxInputChars");
        }
        positionPolicy = Objects.requireNonNull(positionPolicy, "positionPolicy");
    }

    /**
     * Returns conservative defaults compatible with previous Lucene behavior.
     *
     * @return default Lucene analysis options
     */
    public static MystemLuceneAnalysisOptions defaults() {
        return new MystemLuceneAnalysisOptions(
                DEFAULT_MAX_INPUT_CHARS, DEFAULT_MAX_CHUNK_CHARS, MystemLucenePositionPolicy.COMPACT);
    }

    /**
     * Returns options with a custom field limit and default chunking and position behavior.
     *
     * @param maxInputChars maximum number of UTF-16 code units read from one Lucene field
     * @return Lucene analysis options
     */
    public static MystemLuceneAnalysisOptions withMaxInputChars(int maxInputChars) {
        return new MystemLuceneAnalysisOptions(
                maxInputChars,
                Math.min(DEFAULT_MAX_CHUNK_CHARS, maxInputChars),
                MystemLucenePositionPolicy.COMPACT);
    }
}
