package io.github.ulviar.mystem4j.tokenization;

/**
 * Controls how search tokenization handles MyStem tokens that could not be aligned to the original text.
 */
public enum MystemUnmatchedTokenPolicy {
    /**
     * Reject the document with {@link MystemTokenizationException} when a model token has unknown offsets.
     */
    FAIL,

    /**
     * Discard the unaligned model token and synthesize offset-safe tokens from original-text gaps.
     *
     * <p>This is the default. The discarded token's analyses are not attached to a guessed source
     * occurrence; uncovered text receives fallback forms instead. Invalid known ranges still fail.
     */
    SYNTHESIZE_FROM_ORIGINAL_TEXT
}
