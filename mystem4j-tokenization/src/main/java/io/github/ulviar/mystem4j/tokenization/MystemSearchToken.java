package io.github.ulviar.mystem4j.tokenization;

import java.util.List;
import java.util.Objects;

/**
 * Search-oriented token with original-text offsets.
 */
public record MystemSearchToken(
        String text, List<MystemTokenForm> forms, int startOffset, int endOffset, MystemSearchTokenType type) {
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
