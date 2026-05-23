package io.github.ulviar.mystem4j.model;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One MyStem analysis variant for a token.
 */
public record MystemAnalysis(String lemma, MystemGrammar grammar, OptionalDouble weight) {
    public MystemAnalysis {
        lemma = Objects.requireNonNull(lemma, "lemma");
        grammar = Objects.requireNonNull(grammar, "grammar");
        weight = Objects.requireNonNull(weight, "weight");
    }
}
