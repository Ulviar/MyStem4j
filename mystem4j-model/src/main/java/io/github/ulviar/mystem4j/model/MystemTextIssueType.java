package io.github.ulviar.mystem4j.model;

/**
 * Categories of non-fatal text preparation and token alignment diagnostics.
 *
 * <p>See {@link MystemTextIssue} for the meaning of diagnostic offsets and lengths. Replacement
 * diagnostics identify source ranges; an unmatched-token diagnostic does not.
 */
public enum MystemTextIssueType {
    /**
     * MyStem returned a surface that could not be aligned; both token offsets are {@code -1}.
     * The issue reports the alignment cursor and returned surface length, not a matched source range.
     */
    UNMATCHED_TOKEN,
    /** An isolated UTF-16 surrogate code unit was replaced with {@code U+FFFD}. */
    UNPAIRED_SURROGATE,
    /**
     * An unsafe ISO control character was replaced with a space. CR and LF are included only when
     * {@link MystemTextPreprocessor#prepareJsonLine(String)} is used; tab is preserved in both modes.
     */
    CONTROL_CHARACTER,
    /**
     * A Unicode noncharacter was replaced with one space. A supplementary noncharacter has a
     * diagnostic length of two UTF-16 code units despite its one-unit replacement.
     */
    NONCHARACTER
}
