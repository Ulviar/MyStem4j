package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * One MyStem output item aligned to a range in the original text.
 *
 * <p>Offsets are Java UTF-16 indices, with an inclusive start and an exclusive end. Both are {@code -1}
 * when alignment is unknown. {@code text} is the surface returned by MyStem, not necessarily
 * {@code originalText.substring(startOffset, endOffset)}: MyStem may omit soft hyphens, and text preparation
 * may replace unsafe characters. Use the containing document's original text and the known range to recover
 * the source slice.
 *
 * @param text surface returned by MyStem, possibly empty
 * @param startOffset inclusive original-text UTF-16 offset, or {@code -1} when unknown
 * @param endOffset exclusive original-text UTF-16 offset, or {@code -1} when unknown
 * @param analyses analysis variants in MyStem order, copied into an immutable list
 */
public record MystemToken(String text, int startOffset, int endOffset, List<MystemAnalysis> analyses) {
    /**
     * Creates a token with either two known offsets or two unknown offsets.
     *
     * @param text surface returned by MyStem
     * @param startOffset inclusive original-text UTF-16 offset, or {@code -1}
     * @param endOffset exclusive original-text UTF-16 offset, or {@code -1}
     * @param analyses analysis variants in MyStem order
     * @throws IllegalArgumentException if offsets are below {@code -1}, mix known and unknown values,
     *     or the known end precedes the start
     * @throws NullPointerException if text, analyses, or an analysis is {@code null}
     */
    public MystemToken {
        text = Objects.requireNonNull(text, "text");
        if (startOffset < -1 || endOffset < -1) {
            throw new IllegalArgumentException("offsets must be non-negative or -1 when unknown");
        }
        if ((startOffset == -1) != (endOffset == -1)) {
            throw new IllegalArgumentException("offsets must be both known or both unknown");
        }
        if (startOffset >= 0 && endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than or equal to startOffset");
        }
        analyses = List.copyOf(Objects.requireNonNull(analyses, "analyses"));
    }

    /**
     * Reports whether the token has an original-text range.
     *
     * @return {@code true} if both offsets are non-negative; an empty known range also returns {@code true}
     */
    public boolean hasKnownOffsets() {
        return startOffset >= 0 && endOffset >= 0;
    }
}
