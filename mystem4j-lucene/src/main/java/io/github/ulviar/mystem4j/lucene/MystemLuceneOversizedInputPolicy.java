package io.github.ulviar.mystem4j.lucene;

/**
 * Controls how the Lucene tokenizer handles a field longer than the configured input limit.
 */
public enum MystemLuceneOversizedInputPolicy {
    /**
     * Fail analysis with {@link java.io.IOException} when the field exceeds {@code maxInputChars}.
     *
     * <p>The limit is detected while reading; earlier chunks may already have been analyzed or emitted.
     * A caller handling streams directly must discard partial results when analysis fails.
     */
    FAIL,

    /**
     * Analyze only the prefix that fits the configured limit, without splitting a valid UTF-16 surrogate pair.
     *
     * <p>The remainder is read and discarded so final offsets still cover the complete original field.
     * This limits indexed content, not the total number of characters read from the reader.
     */
    TRUNCATE_AT_CODE_POINT_BOUNDARY
}
