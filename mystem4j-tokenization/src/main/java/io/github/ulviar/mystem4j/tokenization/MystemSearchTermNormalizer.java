package io.github.ulviar.mystem4j.tokenization;

import java.util.Objects;

/**
 * Character normalization shared by indexed aliases and single-term query normalization.
 *
 * <p>Removes U+00AD SOFT HYPHEN and U+0301/U+0341 acute marks, and applies
 * {@link Character#toLowerCase(int)} to each remaining Unicode code point. Other marks,
 * punctuation, wildcard characters and unpaired surrogate code units are preserved.
 * Normalizing a prefix is independent of following text when the boundary does not split
 * a surrogate pair. The operation performs neither morphology nor suffix expansion.
 *
 * <p>This is simple Unicode lowercasing, not locale-specific or full Unicode case folding.
 * For example, a lowercase Greek final sigma remains a final sigma. Search-token forms
 * retain their existing lowercase literal forms as well as nonempty normalized aliases.
 * Normalized terms are search values; they must not be used to calculate source offsets.
 */
public final class MystemSearchTermNormalizer {
    private MystemSearchTermNormalizer() {}

    /**
     * Normalizes one term or a literal portion of a query pattern without splitting it.
     *
     * @param text term or pattern fragment; not {@code null}
     * @return normalized text, possibly empty when the input is empty or contains only removed marks
     * @throws NullPointerException when {@code text} is {@code null}
     */
    public static String normalize(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder result = null;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            boolean removed = codePoint == 0x00AD || codePoint == 0x0301 || codePoint == 0x0341;
            int lowercase = Character.toLowerCase(codePoint);
            if (result == null && (removed || lowercase != codePoint)) {
                result = new StringBuilder(text.length()).append(text, 0, offset);
            }
            if (result != null && !removed) {
                result.appendCodePoint(lowercase);
            }
            offset += Character.charCount(codePoint);
        }
        return result == null ? text : result.toString();
    }
}
