package io.github.ulviar.mystem4j.tokenization;

/**
 * Controls how MyStem analysis variants are converted to lemma forms.
 */
public enum MystemLemmaSelectionPolicy {
    /**
     * Emit distinct lemmas from all MyStem analysis variants.
     */
    ALL,

    /**
     * Emit one lemma from the highest-weight analysis variant.
     *
     * <p>If no variant has a weight, the first non-empty lemma is used.
     */
    BEST_WEIGHT
}
