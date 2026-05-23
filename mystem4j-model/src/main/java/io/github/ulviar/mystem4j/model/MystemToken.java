package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * One MyStem output item aligned to a range in the original text.
 */
public record MystemToken(String text, int startOffset, int endOffset, List<MystemAnalysis> analyses) {
    public MystemToken {
        text = Objects.requireNonNull(text, "text");
        if (startOffset < -1 || endOffset < -1) {
            throw new IllegalArgumentException("offsets must be non-negative or -1 when unknown");
        }
        if (startOffset >= 0 && endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than or equal to startOffset");
        }
        analyses = List.copyOf(Objects.requireNonNull(analyses, "analyses"));
    }

    public boolean hasKnownOffsets() {
        return startOffset >= 0 && endOffset >= 0;
    }
}
