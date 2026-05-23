package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Parsed MyStem document with token offsets relative to the original Java string.
 */
public record MystemDocument(String originalText, List<MystemToken> tokens, List<MystemTextIssue> issues) {
    public MystemDocument {
        originalText = originalText == null ? "" : originalText;
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }
}
