package io.github.ulviar.mystem4j.tokenization;

/**
 * Policy switches for search-token preparation.
 *
 * <p>Offset safety, gap synthesis, suffix recovery, and lemma fallback forms are always enabled. These options only
 * control semantic enrichment that is useful for some search applications but should not be forced on every Lucene
 * pipeline.
 */
public record MystemSearchTokenizerOptions(
        boolean classifyNumbers,
        boolean mergeUrls,
        boolean mergeEmails,
        boolean classifyCurrencies,
        boolean expandCurrencyForms) {
    public MystemSearchTokenizerOptions {
        if (expandCurrencyForms && !classifyCurrencies) {
            throw new IllegalArgumentException("expandCurrencyForms requires classifyCurrencies.");
        }
    }

    /**
     * Conservative morphology-oriented defaults.
     *
     * <p>Tokens keep safe offsets and forms, but numbers, URLs, emails, and currencies are not exposed as separate
     * semantic token types.
     *
     * @return conservative options
     */
    public static MystemSearchTokenizerOptions conservative() {
        return new MystemSearchTokenizerOptions(false, false, false, false, false);
    }

    /**
     * General search defaults.
     *
     * <p>Numbers and currency symbols are exposed as token types, but URL/email entity merging and localized currency
     * expansion stay disabled.
     *
     * @return search-oriented options
     */
    public static MystemSearchTokenizerOptions search() {
        return new MystemSearchTokenizerOptions(true, false, false, true, false);
    }

    /**
     * Rich entity-aware defaults matching the legacy tokenizer behavior.
     *
     * @return entity-aware options
     */
    public static MystemSearchTokenizerOptions entityAware() {
        return new MystemSearchTokenizerOptions(true, true, true, true, true);
    }
}
