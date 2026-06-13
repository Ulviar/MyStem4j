package io.github.ulviar.mystem4j.tokenization;

import java.util.Objects;

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
        boolean expandCurrencyForms,
        MystemUnmatchedTokenPolicy unmatchedTokenPolicy,
        MystemLemmaSelectionPolicy lemmaSelectionPolicy) {
    public MystemSearchTokenizerOptions {
        unmatchedTokenPolicy = Objects.requireNonNull(unmatchedTokenPolicy, "unmatchedTokenPolicy");
        lemmaSelectionPolicy = Objects.requireNonNull(lemmaSelectionPolicy, "lemmaSelectionPolicy");
        if (expandCurrencyForms && !classifyCurrencies) {
            throw new IllegalArgumentException("expandCurrencyForms requires classifyCurrencies.");
        }
    }

    public MystemSearchTokenizerOptions(
            boolean classifyNumbers,
            boolean mergeUrls,
            boolean mergeEmails,
            boolean classifyCurrencies,
            boolean expandCurrencyForms,
            MystemUnmatchedTokenPolicy unmatchedTokenPolicy) {
        this(
                classifyNumbers,
                mergeUrls,
                mergeEmails,
                classifyCurrencies,
                expandCurrencyForms,
                unmatchedTokenPolicy,
                MystemLemmaSelectionPolicy.ALL);
    }

    public MystemSearchTokenizerOptions(
            boolean classifyNumbers,
            boolean mergeUrls,
            boolean mergeEmails,
            boolean classifyCurrencies,
            boolean expandCurrencyForms) {
        this(
                classifyNumbers,
                mergeUrls,
                mergeEmails,
                classifyCurrencies,
                expandCurrencyForms,
                MystemUnmatchedTokenPolicy.SYNTHESIZE_FROM_ORIGINAL_TEXT,
                MystemLemmaSelectionPolicy.ALL);
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
        return builder().build();
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
        return builder()
                .classifyNumbers(true)
                .classifyCurrencies(true)
                .build();
    }

    /**
     * Rich entity-aware defaults matching the legacy tokenizer behavior.
     *
     * @return entity-aware options
     */
    public static MystemSearchTokenizerOptions entityAware() {
        return builder()
                .classifyNumbers(true)
                .mergeUrls(true)
                .mergeEmails(true)
                .classifyCurrencies(true)
                .expandCurrencyForms(true)
                .build();
    }

    /**
     * Returns a builder for explicit option configuration.
     *
     * @return options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder initialized from this option set.
     *
     * @return options builder
     */
    public Builder toBuilder() {
        return builder()
                .classifyNumbers(classifyNumbers)
                .mergeUrls(mergeUrls)
                .mergeEmails(mergeEmails)
                .classifyCurrencies(classifyCurrencies)
                .expandCurrencyForms(expandCurrencyForms)
                .unmatchedTokenPolicy(unmatchedTokenPolicy)
                .lemmaSelectionPolicy(lemmaSelectionPolicy);
    }

    public static final class Builder {
        private boolean classifyNumbers;
        private boolean mergeUrls;
        private boolean mergeEmails;
        private boolean classifyCurrencies;
        private boolean expandCurrencyForms;
        private MystemUnmatchedTokenPolicy unmatchedTokenPolicy =
                MystemUnmatchedTokenPolicy.SYNTHESIZE_FROM_ORIGINAL_TEXT;
        private MystemLemmaSelectionPolicy lemmaSelectionPolicy = MystemLemmaSelectionPolicy.ALL;

        private Builder() {}

        public Builder classifyNumbers(boolean classifyNumbers) {
            this.classifyNumbers = classifyNumbers;
            return this;
        }

        public Builder mergeUrls(boolean mergeUrls) {
            this.mergeUrls = mergeUrls;
            return this;
        }

        public Builder mergeEmails(boolean mergeEmails) {
            this.mergeEmails = mergeEmails;
            return this;
        }

        public Builder classifyCurrencies(boolean classifyCurrencies) {
            this.classifyCurrencies = classifyCurrencies;
            return this;
        }

        public Builder expandCurrencyForms(boolean expandCurrencyForms) {
            this.expandCurrencyForms = expandCurrencyForms;
            return this;
        }

        public Builder unmatchedTokenPolicy(MystemUnmatchedTokenPolicy unmatchedTokenPolicy) {
            this.unmatchedTokenPolicy = Objects.requireNonNull(unmatchedTokenPolicy, "unmatchedTokenPolicy");
            return this;
        }

        public Builder lemmaSelectionPolicy(MystemLemmaSelectionPolicy lemmaSelectionPolicy) {
            this.lemmaSelectionPolicy = Objects.requireNonNull(lemmaSelectionPolicy, "lemmaSelectionPolicy");
            return this;
        }

        public MystemSearchTokenizerOptions build() {
            return new MystemSearchTokenizerOptions(
                    classifyNumbers,
                    mergeUrls,
                    mergeEmails,
                    classifyCurrencies,
                    expandCurrencyForms,
                    unmatchedTokenPolicy,
                    lemmaSelectionPolicy);
        }
    }
}
