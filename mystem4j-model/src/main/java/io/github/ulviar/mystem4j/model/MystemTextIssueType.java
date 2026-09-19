package io.github.ulviar.mystem4j.model;

/**
 * Categories of non-fatal text preparation and token alignment diagnostics.
 */
public enum MystemTextIssueType {
    /** MyStem returned a surface that could not be aligned; the token has unknown offsets. */
    UNMATCHED_TOKEN,
    /** An isolated UTF-16 surrogate code unit was replaced with {@code U+FFFD}. */
    UNPAIRED_SURROGATE,
    /** A control character was replaced with a space, including CR/LF in JSON-line preparation. */
    CONTROL_CHARACTER,
    /** A Unicode noncharacter was replaced with a space. */
    NONCHARACTER
}
