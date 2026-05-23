package io.github.ulviar.mystem4j.model;

import java.util.Objects;

/**
 * Non-fatal issue detected while preparing or aligning text.
 */
public record MystemTextIssue(MystemTextIssueType type, String message, int offset, int length) {
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
