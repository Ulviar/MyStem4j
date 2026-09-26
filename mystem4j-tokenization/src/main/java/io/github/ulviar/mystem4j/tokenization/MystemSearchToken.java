package io.github.ulviar.mystem4j.tokenization;

import java.util.List;
import java.util.Objects;

/**
 * An immutable source token with one or more alternative search forms.
 *
 * <p>Tokens returned by {@link MystemSearchTokenizer} retain the exact source substring and half-open
 * UTF-16 range. Forms may have different text and length; they describe alternatives at the same source
 * position rather than successive words. The forms list is defensively copied and unmodifiable.
 *
 * @param text source text, including any characters removed from search forms
 * @param forms nonempty list of search alternatives
 * @param startOffset inclusive start in the original Java string, in UTF-16 code units
 * @param endOffset exclusive end in the original Java string, in UTF-16 code units
 * @param type coarse semantic or separator classification
 */
public record MystemSearchToken(
        String text, List<MystemTokenForm> forms, int startOffset, int endOffset, MystemSearchTokenType type) {
    /**
     * Creates a token and copies its forms.
     *
     * <p>This constructor checks numeric offset ordering, but has no source document against which to
     * validate the text or surrogate boundaries. {@link MystemSearchTokenizer} enforces those stronger
     * invariants for generated tokens.
     *
     * @param text source text
     * @param forms nonempty list of non-null forms
     * @param startOffset nonnegative inclusive UTF-16 offset
     * @param endOffset exclusive UTF-16 offset, at least {@code startOffset}
     * @param type token classification
     * @throws NullPointerException if text, forms, a form element, or type is {@code null}
     * @throws IllegalArgumentException if forms are empty or offsets are negative or reversed
     */
    public MystemSearchToken {
        text = Objects.requireNonNull(text, "text");
        forms = List.copyOf(Objects.requireNonNull(forms, "forms"));
        type = Objects.requireNonNull(type, "type");
        if (forms.isEmpty()) {
            throw new IllegalArgumentException("forms must not be empty");
        }
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("invalid offsets");
        }
    }
}
