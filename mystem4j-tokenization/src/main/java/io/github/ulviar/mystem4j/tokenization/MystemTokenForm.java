package io.github.ulviar.mystem4j.tokenization;

import java.util.Objects;

/**
 * Search form emitted for one MyStem-backed token.
 */
public record MystemTokenForm(String text, boolean keyword) {
    public MystemTokenForm {
        text = Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be empty");
        }
    }
}
