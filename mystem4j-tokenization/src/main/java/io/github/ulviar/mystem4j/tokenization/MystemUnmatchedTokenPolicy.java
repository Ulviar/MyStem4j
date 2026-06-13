package io.github.ulviar.mystem4j.tokenization;

/**
 * Controls how search tokenization handles MyStem tokens that could not be aligned to the original text.
 */
public enum MystemUnmatchedTokenPolicy {
    /**
     * Reject the whole document when a MyStem token has unknown offsets.
     */
    FAIL,

    /**
     * Ignore the unaligned MyStem token and synthesize offset-safe tokens from the original text gaps.
     */
    SYNTHESIZE_FROM_ORIGINAL_TEXT
}
