package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Text prepared for MyStem with offset mapping back to the original Java string.
 */
public final class MystemPreparedText {
    private final String originalText;
    private final String text;
    private final List<MystemOffsetMapping> mappings;
    private final List<MystemTextIssue> issues;

    MystemPreparedText(
            String originalText, String text, List<MystemOffsetMapping> mappings, List<MystemTextIssue> issues) {
        this.originalText = Objects.requireNonNull(originalText, "originalText");
        this.text = Objects.requireNonNull(text, "text");
        this.mappings = List.copyOf(Objects.requireNonNull(mappings, "mappings"));
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        validateMappings(this.originalText, this.text, this.mappings);
    }

    public String originalText() {
        return originalText;
    }

    public String text() {
        return text;
    }

    public List<MystemTextIssue> issues() {
        return issues;
    }

    public int originalOffsetFor(int preparedOffset) {
        if (preparedOffset < 0 || preparedOffset > text.length()) {
            throw new IllegalArgumentException("preparedOffset is out of range: " + preparedOffset);
        }
        if (preparedOffset == text.length()) {
            return originalText.length();
        }
        int low = 0;
        int high = mappings.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            MystemOffsetMapping mapping = mappings.get(middle);
            if (preparedOffset < mapping.preparedStart()) {
                high = middle - 1;
            } else if (preparedOffset >= mapping.preparedEnd()) {
                low = middle + 1;
            } else {
                int shifted = mapping.originalStart() + preparedOffset - mapping.preparedStart();
                return Math.min(mapping.originalEnd(), shifted);
            }
        }
        throw new IllegalStateException("validated prepared text does not cover offset " + preparedOffset);
    }

    private static void validateMappings(String originalText, String text, List<MystemOffsetMapping> mappings) {
        if (text.isEmpty() || originalText.isEmpty()) {
            if (!mappings.isEmpty()) {
                throw new IllegalArgumentException("empty prepared or original text must not have offset mappings");
            }
            if (text.length() != originalText.length()) {
                throw new IllegalArgumentException("empty prepared text and original text must match");
            }
            return;
        }
        int expectedPreparedStart = 0;
        int expectedOriginalStart = 0;
        for (MystemOffsetMapping mapping : mappings) {
            if (mapping.preparedStart() != expectedPreparedStart) {
                throw new IllegalArgumentException("offset mappings must cover prepared text without gaps");
            }
            if (mapping.originalStart() != expectedOriginalStart) {
                throw new IllegalArgumentException("offset mappings must cover original text without gaps");
            }
            if (mapping.preparedStart() == mapping.preparedEnd()) {
                throw new IllegalArgumentException("offset mappings must not contain empty prepared ranges");
            }
            if (mapping.originalStart() == mapping.originalEnd()) {
                throw new IllegalArgumentException("offset mappings must not contain empty original ranges");
            }
            if (mapping.preparedEnd() > text.length()) {
                throw new IllegalArgumentException("offset mapping exceeds prepared text length");
            }
            if (mapping.originalEnd() > originalText.length()) {
                throw new IllegalArgumentException("offset mapping exceeds original text length");
            }
            expectedPreparedStart = mapping.preparedEnd();
            expectedOriginalStart = mapping.originalEnd();
        }
        if (expectedPreparedStart != text.length()) {
            throw new IllegalArgumentException("offset mappings must cover prepared text");
        }
        if (expectedOriginalStart != originalText.length()) {
            throw new IllegalArgumentException("offset mappings must cover original text");
        }
    }
}
