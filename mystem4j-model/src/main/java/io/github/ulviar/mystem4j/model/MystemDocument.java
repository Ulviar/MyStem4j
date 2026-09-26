package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of MyStem output and its alignment to the caller's original Java string.
 *
 * <p>Documents returned by {@link MystemJsonParser} retain every token in output order, including
 * items with empty analyses or unknown offsets. Tokens can leave gaps: punctuation and whitespace
 * absent from MyStem output are not synthesized here. Inspect {@link #issues()} before assuming
 * that every token can be located in the source.
 *
 * <p>Token ranges use Java UTF-16 indices into {@link #originalText()}. They are half-open, and may
 * cover a different string from {@link MystemToken#text()}. See {@link MystemToken} for source
 * recovery and unknown-offset handling. Instances and their lists can be shared between threads.
 *
 * @param originalText caller's text before any preprocessing
 * @param tokens parsed items in MyStem output order, copied into an immutable list
 * @param issues non-fatal diagnostics, copied into an immutable list; parser-created documents
 *     place preprocessing issues before alignment issues
 */
public record MystemDocument(String originalText, List<MystemToken> tokens, List<MystemTextIssue> issues) {
    /**
     * Creates a document and copies its token and issue lists.
     *
     * <p>This constructor does not align or validate token ranges against the original text, reorder
     * entries, or infer issues. Use {@link MystemJsonParser} to construct a document from MyStem output.
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
