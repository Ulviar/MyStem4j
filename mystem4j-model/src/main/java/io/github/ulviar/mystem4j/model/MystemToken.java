package io.github.ulviar.mystem4j.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable MyStem output item with its surface, analyses, and original-text range when known.
 *
 * <p>Offsets are Java UTF-16 indices, with an inclusive start and an exclusive end. Both are {@code -1}
 * when alignment is unknown. {@code text} is the surface returned by MyStem, not necessarily
 * {@code originalText.substring(startOffset, endOffset)}: MyStem may omit soft hyphens, and text preparation
 * may replace unsafe characters. Use the containing document's original text and the known range to recover
 * the source slice.
 *
 * <p>A token is not necessarily a word: it can contain punctuation, whitespace, or an empty
 * surface, depending on the supplied MyStem output. An empty analysis list means that no analyses
 * were supplied; it does not by itself classify the surface as punctuation or an unknown word.
 * Neither the surface nor the analyses are normalized or reordered by this record.
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
     * <p>The range is not checked against a document's length. The surface length need not equal
     * {@code endOffset - startOffset}, and zero-length known ranges are allowed.
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
     * <p>This only examines the stored offsets; it does not check whether they fit a particular
     * source string. Parser-created tokens use the containing {@link MystemDocument#originalText()}.
     *
     * @return {@code true} if both offsets are non-negative; an empty known range also returns {@code true}
     */
    public boolean hasKnownOffsets() {
        return startOffset >= 0 && endOffset >= 0;
    }
}
