package io.github.ulviar.mystem4j.model;

import java.util.Objects;
import java.util.Set;

/**
 * Inflection grammemes for one MyStem grammar alternative.
 */
public record MystemGrammarVariant(Set<String> grammemes) {
    public MystemGrammarVariant {
        grammemes = Set.copyOf(Objects.requireNonNull(grammemes, "grammemes"));
    }
}
