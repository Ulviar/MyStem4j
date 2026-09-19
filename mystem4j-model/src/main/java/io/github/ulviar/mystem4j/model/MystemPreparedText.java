package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Text prepared for MyStem with offset mapping back to the original Java string.
 *
 * <p>Instances are immutable and produced by {@link MystemTextPreprocessor}. Preparation can change
 * the text length; use {@link #originalOffsetFor(int)} for both endpoints of a prepared-text range.
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

    /**
     * Returns the caller's text before preparation.
     *
     * @return original Java string, unchanged
     */
    public String originalText() {
        return originalText;
    }

    /**
     * Returns the prepared text to send to MyStem.
     *
     * @return text after unsafe-character replacement
     */
    public String text() {
        return text;
    }

    /**
     * Returns the character replacements performed during preparation.
     *
     * @return immutable issues in source order, with offsets in the original text
     */
    public List<MystemTextIssue> issues() {
        return issues;
    }

    /**
     * Maps a prepared-text UTF-16 position to the corresponding original-text position.
     *
     * <p>The mapping is defined for every code-unit position from zero through {@code text().length()},
     * including the end position. It is monotonic, and the prepared end maps to
     * {@code originalText().length()}. Positions are not restricted to Unicode code-point boundaries.
     *
     * @param preparedOffset position in the prepared Java string, from zero through its length inclusive
     * @return corresponding UTF-16 position in the original Java string
     * @throws IllegalArgumentException if the position is negative or exceeds the prepared text length
     */
    public int originalOffsetFor(int preparedOffset) {
        if (preparedOffset < 0 || preparedOffset > text.length()) {
            throw new IllegalArgumentException("preparedOffset is out of range: " + preparedOffset);
        }
        if (mappings.isEmpty()) {
            return preparedOffset;
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
        if (mappings.isEmpty()) {
            if (text.length() != originalText.length()) {
                throw new IllegalArgumentException("identity offset mapping requires equal text lengths");
            }
            return;
        }
        if (text.isEmpty() || originalText.isEmpty()) {
            throw new IllegalArgumentException("empty prepared or original text must not have explicit mappings");
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
