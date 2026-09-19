package io.github.ulviar.mystem4j.model;

import java.util.Objects;

/**
 * Non-fatal issue detected while preparing or aligning text.
 *
 * <p>For character replacements, {@code offset} and {@code length} describe the replaced range in
 * the original Java string. For {@link MystemTextIssueType#UNMATCHED_TOKEN}, {@code offset} is the
 * original-text alignment cursor and {@code length} is the unmatched MyStem surface length; together
 * they do not identify a matched source range and may exceed the remaining source length.
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
