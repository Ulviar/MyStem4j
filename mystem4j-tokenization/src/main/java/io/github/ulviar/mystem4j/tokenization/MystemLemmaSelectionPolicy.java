package io.github.ulviar.mystem4j.tokenization;

/**
 * Controls how MyStem analysis variants are converted to lemma forms.
 */
public enum MystemLemmaSelectionPolicy {
    /**
     * Emit distinct nonempty lemmas in MyStem analysis order.
     */
    ALL,

    /**
     * Emit the nonempty lemma from the highest-weight analysis variant.
     *
     * <p>Variants without a lemma or weight are ignored during weighted selection; ties keep the
     * first variant. If no nonempty lemma has a weight, the first nonempty lemma is used. Surface
     * fallback forms are still available when every lemma is empty.
     */
    BEST_WEIGHT
}
