package io.github.ulviar.mystem4j.lucene;

/**
 * Controls how the Lucene tokenizer handles a field longer than the configured input limit.
 */
public enum MystemLuceneOversizedInputPolicy {
    /**
     * Reject the whole field when it exceeds {@code maxInputChars}.
     */
    FAIL,

    /**
     * Analyze only the prefix that fits the configured limit, without ending on an unpaired UTF-16 surrogate.
     */
    TRUNCATE_AT_CODE_POINT_BOUNDARY
}
