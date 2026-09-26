package io.github.ulviar.mystem4j.model;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable morphological reading of a token: its lemma, grammar, and optional weight.
 *
 * <p>A token may have several readings, retained in MyStem output order by
 * {@link MystemToken#analyses()}. The model does not select a preferred reading, normalize the lemma,
 * or interpret a weight as a probability. An absent weight differs from a present zero.
 *
 * @param lemma dictionary form from {@code lex}, or an empty string when the field is absent
 * @param grammar parsed {@code gr} value, including the unmodified grammar string
 * @param weight optional numeric {@code wt} value; no probability range is imposed by the model
 */
public record MystemAnalysis(String lemma, MystemGrammar grammar, OptionalDouble weight) {
    /**
     * Creates an immutable analysis without changing the lemma, grammar, or weight.
     *
     * @param lemma dictionary form
     * @param grammar parsed grammar
     * @param weight weight supplied by MyStem, or an empty optional
     * @throws NullPointerException if any argument is {@code null}
     */
    public MystemAnalysis {
        lemma = Objects.requireNonNull(lemma, "lemma");
        grammar = Objects.requireNonNull(grammar, "grammar");
        weight = Objects.requireNonNull(weight, "weight");
    }
}
