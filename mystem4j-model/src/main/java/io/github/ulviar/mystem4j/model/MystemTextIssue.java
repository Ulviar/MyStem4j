package io.github.ulviar.mystem4j.model;

import java.util.Objects;

/**
 * Immutable diagnostic for a character replacement or a token that could not be aligned.
 *
 * <p>For character replacements, {@code offset} and {@code length} describe the replaced range in
 * the original Java string. For {@link MystemTextIssueType#UNMATCHED_TOKEN}, {@code offset} is the
 * original-text alignment cursor and {@code length} is the unmatched MyStem surface length; together
 * they do not identify a matched source range and may exceed the remaining source length.
 * Use {@link #type()} to distinguish those cases, and {@link MystemToken#hasKnownOffsets()} to
 * decide whether a token can be sliced from a parser-created document.
 *
 * <p>The message is for people, not for matching error conditions in code. Use the enum type for
 * programmatic handling. An issue does not imply a failed parse: parser-created documents retain
 * their tokens and collect issues in {@link MystemDocument#issues()}.
 *
 * @param type issue category
 * @param message human-readable diagnostic; not intended as a stable machine-readable identifier
 * @param offset original-text UTF-16 position, or {@code -1} when unknown
 * @param length length in UTF-16 code units, never negative
 */
public record MystemTextIssue(MystemTextIssueType type, String message, int offset, int length) {
    /**
     * Creates a text diagnostic.
     *
     * <p>The constructor validates the numeric bounds only; it does not check the issue against
     * a source string or require a particular length for an issue type.
     *
     * @param type issue category
     * @param message human-readable diagnostic
     * @param offset original-text UTF-16 position, or {@code -1}
     * @param length length in UTF-16 code units
     * @throws IllegalArgumentException if the offset is below {@code -1} or the length is negative
     * @throws NullPointerException if type or message is {@code null}
     */
    public MystemTextIssue {
        type = Objects.requireNonNull(type, "type");
        message = Objects.requireNonNull(message, "message");
        if (offset < -1) {
            throw new IllegalArgumentException("offset must be non-negative or -1 when unknown");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
    }
}
