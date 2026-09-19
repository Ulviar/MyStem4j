package io.github.ulviar.mystem4j.model;

import java.util.Objects;
import java.util.Set;

/**
 * Inflection grammemes for one MyStem grammar alternative.
 *
 * @param grammemes features of this alternative, copied into an immutable set with unspecified iteration order
 */
public record MystemGrammarVariant(Set<String> grammemes) {
    /**
     * Creates an alternative and copies its grammemes.
     *
     * @param grammemes features of this alternative
     * @throws NullPointerException if the set or one of its elements is {@code null}
     */
    public MystemGrammarVariant {
        grammemes = Set.copyOf(Objects.requireNonNull(grammemes, "grammemes"));
    }
}
