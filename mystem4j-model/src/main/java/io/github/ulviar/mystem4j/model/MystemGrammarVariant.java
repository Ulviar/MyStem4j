package io.github.ulviar.mystem4j.model;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable set of inflection grammemes for one MyStem grammar alternative.
 *
 * <p>These features supplement {@link MystemGrammar#commonGrammemes()}; the part of speech is stored
 * separately in {@link MystemGrammar#partOfSpeech()}. An empty set is valid, for example for the
 * empty right side of {@code PR=}. Duplicate features are removed, and their iteration order is
 * unspecified.
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
