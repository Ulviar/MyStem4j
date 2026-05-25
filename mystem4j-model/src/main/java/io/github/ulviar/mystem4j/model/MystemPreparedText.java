package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Text prepared for MyStem with offset mapping back to the original Java string.
 */
public record MystemPreparedText(
        String originalText, String text, List<MystemOffsetMapping> mappings, List<MystemTextIssue> issues) {
    public MystemPreparedText {
        originalText = Objects.requireNonNull(originalText, "originalText");
        text = Objects.requireNonNull(text, "text");
        mappings = List.copyOf(Objects.requireNonNull(mappings, "mappings"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public int originalOffsetFor(int preparedOffset) {
        if (preparedOffset < 0 || preparedOffset > text.length()) {
            throw new IllegalArgumentException("preparedOffset is out of range: " + preparedOffset);
        }
        for (MystemOffsetMapping mapping : mappings) {
            if (preparedOffset == mapping.preparedEnd()) {
                return mapping.originalEnd();
            }
            if (preparedOffset >= mapping.preparedStart() && preparedOffset < mapping.preparedEnd()) {
                return Math.min(mapping.originalEnd(), mapping.originalStart() + preparedOffset - mapping.preparedStart());
            }
        }
        return originalText.length();
    }
}
