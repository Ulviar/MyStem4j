package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Parsed MyStem document with token offsets relative to the original Java string.
 *
 * @param originalText caller's text before any preprocessing
 * @param tokens parsed items in MyStem output order, copied into an immutable list
 * @param issues preprocessing issues followed by alignment issues, copied into an immutable list
 */
public record MystemDocument(String originalText, List<MystemToken> tokens, List<MystemTextIssue> issues) {
    /**
     * Creates a document and copies its token and issue lists.
     *
     * <p>This constructor does not align or validate token ranges against the original text.
     *
     * @param originalText caller's original text
     * @param tokens tokens in output order
     * @param issues non-fatal preparation and alignment issues
     * @throws NullPointerException if an argument or a list element is {@code null}
     */
    public MystemDocument {
        originalText = Objects.requireNonNull(originalText, "originalText");
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }
}
