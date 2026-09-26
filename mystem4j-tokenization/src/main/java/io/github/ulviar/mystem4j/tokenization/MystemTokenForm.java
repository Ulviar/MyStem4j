package io.github.ulviar.mystem4j.tokenization;

import java.util.Objects;

/**
 * An immutable, nonempty term that can be indexed for a source token.
 *
 * <p>The term may be a lemma, surface fallback, or normalized alias. Its length does not determine the
 * source range; use the containing {@link MystemSearchToken}'s offsets. The keyword flag asks downstream
 * filters to preserve the term instead of stemming or lowercasing it again.
 *
 * @param text nonempty search term, not necessarily the original spelling
 * @param keyword whether downstream normalization filters should preserve this term
 */
public record MystemTokenForm(String text, boolean keyword) {
    /**
     * Creates a search form.
     *
     * @param text nonempty search term
     * @param keyword whether downstream normalization filters should preserve this term
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code text} is empty
     */
    public MystemTokenForm {
        text = Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be empty");
        }
    }
}
