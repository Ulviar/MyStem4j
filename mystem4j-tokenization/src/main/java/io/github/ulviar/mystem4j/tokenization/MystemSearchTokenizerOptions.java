package io.github.ulviar.mystem4j.tokenization;

import java.util.Objects;

/**
 * Immutable policies for search forms, unaligned tokens, and optional entity enrichment.
 *
 * <p>Offset safety, gap synthesis, suffix recovery, and fallback forms are always enabled. Start with
 * {@link #conservative()}, {@link #search()}, or {@link #entityAware()}, then use {@link #toBuilder()}
 * to adjust individual policies. Options can be shared between threads; builders cannot.
 */
public final class MystemSearchTokenizerOptions {
    private final boolean classifyNumbers;
    private final boolean mergeUrls;
    private final boolean mergeEmails;
    private final boolean classifyCurrencies;
    private final boolean expandCurrencyForms;
    private final MystemUnmatchedTokenPolicy unmatchedTokenPolicy;
    private final MystemLemmaSelectionPolicy lemmaSelectionPolicy;

    private MystemSearchTokenizerOptions(Builder builder) {
        classifyNumbers = builder.classifyNumbers;
        mergeUrls = builder.mergeUrls;
        mergeEmails = builder.mergeEmails;
        classifyCurrencies = builder.classifyCurrencies;
        expandCurrencyForms = builder.expandCurrencyForms;
        unmatchedTokenPolicy = Objects.requireNonNull(builder.unmatchedTokenPolicy, "unmatchedTokenPolicy");
        lemmaSelectionPolicy = Objects.requireNonNull(builder.lemmaSelectionPolicy, "lemmaSelectionPolicy");
        if (expandCurrencyForms && !classifyCurrencies) {
            throw new IllegalArgumentException("expandCurrencyForms requires classifyCurrencies.");
        }
    }

    /**
     * Returns whether numeric fragments receive the {@link MystemSearchTokenType#NUMBER} type.
     *
     * @return {@code true} when numeric classification is enabled
     */
    public boolean classifyNumbers() {
        return classifyNumbers;
    }

    /**
     * Returns whether adjacent URL fragments are merged into one source token with URL and host forms.
     *
     * @return {@code true} when syntactic URL recognition is enabled; no network requests are made
     */
    public boolean mergeUrls() {
        return mergeUrls;
    }

    /**
     * Returns whether adjacent email fragments are merged into one source token with address and domain forms.
     *
     * @return {@code true} when syntactic email recognition is enabled; no network requests are made
     */
    public boolean mergeEmails() {
        return mergeEmails;
    }

    /**
     * Returns whether recognized currency symbols receive the {@link MystemSearchTokenType#CURRENCY} type.
     *
     * @return {@code true} when currency classification is enabled
     */
    public boolean classifyCurrencies() {
        return classifyCurrencies;
    }

    /**
     * Returns whether currency symbols also produce configured localized names and lowercase ISO code forms.
     *
     * @return {@code true} when expansion is enabled; implies {@link #classifyCurrencies()}
     */
    public boolean expandCurrencyForms() {
        return expandCurrencyForms;
    }

    /**
     * Returns the policy for model tokens whose original-text offsets are unknown.
     *
     * @return non-null unmatched-token policy
     */
    public MystemUnmatchedTokenPolicy unmatchedTokenPolicy() {
        return unmatchedTokenPolicy;
    }

    /**
     * Returns how MyStem analysis variants contribute lemma forms.
     *
     * @return non-null lemma-selection policy
     */
    public MystemLemmaSelectionPolicy lemmaSelectionPolicy() {
        return lemmaSelectionPolicy;
    }

    /**
     * Returns morphology-oriented options with all entity switches disabled.
     *
     * <p>Tokens keep safe offsets and forms, but numbers, URLs, emails, and currencies are not exposed as separate
     * semantic token types.
     * Unknown-offset tokens are recovered from source gaps, and all distinct lemmas are retained.
     *
     * @return conservative options
     */
    public static MystemSearchTokenizerOptions conservative() {
        return builder().build();
    }

    /**
     * Returns options that classify numbers and currencies without URL/email merging.
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
     * Returns options with number and currency types, URL/email grouping, and currency-form expansion.
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
     * Returns a new builder initialized to {@link #conservative()} defaults.
     *
     * @return independent mutable builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder initialized from this option set.
     *
     * @return independent mutable builder; subsequent changes do not affect this instance
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

    /**
     * Builds immutable tokenization options from conservative defaults.
     *
     * <p>All enrichment flags start disabled, unmatched tokens are synthesized from original text,
     * and lemma selection starts at {@link MystemLemmaSelectionPolicy#ALL}. A builder is mutable and
     * intended for one thread; previously built options are unaffected by later changes.
     */
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

        /**
         * Enables or disables numeric token types.
         *
         * @param classifyNumbers whether numeric fragments receive the NUMBER type; default {@code false}
         * @return this builder
         */
        public Builder classifyNumbers(boolean classifyNumbers) {
            this.classifyNumbers = classifyNumbers;
            return this;
        }

        /**
         * Enables or disables URL merging and full-value/host forms.
         *
         * @param mergeUrls whether syntactically valid adjacent URL fragments are merged; default {@code false}
         * @return this builder
         */
        public Builder mergeUrls(boolean mergeUrls) {
            this.mergeUrls = mergeUrls;
            return this;
        }

        /**
         * Enables or disables email merging and full-value/domain forms.
         *
         * @param mergeEmails whether syntactically valid adjacent email fragments are merged; default {@code false}
         * @return this builder
         */
        public Builder mergeEmails(boolean mergeEmails) {
            this.mergeEmails = mergeEmails;
            return this;
        }

        /**
         * Enables or disables currency-symbol token types.
         *
         * @param classifyCurrencies whether recognized currency symbols receive the CURRENCY type;
         *         default {@code false}; required when currency-form expansion is enabled
         * @return this builder
         */
        public Builder classifyCurrencies(boolean classifyCurrencies) {
            this.classifyCurrencies = classifyCurrencies;
            return this;
        }

        /**
         * Enables or disables localized currency names and lowercase ISO code forms.
         *
         * @param expandCurrencyForms whether to expand recognized currency symbols; default {@code false}
         * @return this builder
         * @see #classifyCurrencies(boolean)
         */
        public Builder expandCurrencyForms(boolean expandCurrencyForms) {
            this.expandCurrencyForms = expandCurrencyForms;
            return this;
        }

        /**
         * Selects rejection or source-gap recovery for unknown-offset model tokens.
         *
         * @param unmatchedTokenPolicy policy; default {@link MystemUnmatchedTokenPolicy#SYNTHESIZE_FROM_ORIGINAL_TEXT}
         * @return this builder
         * @throws NullPointerException if {@code unmatchedTokenPolicy} is {@code null}
         */
        public Builder unmatchedTokenPolicy(MystemUnmatchedTokenPolicy unmatchedTokenPolicy) {
            this.unmatchedTokenPolicy = Objects.requireNonNull(unmatchedTokenPolicy, "unmatchedTokenPolicy");
            return this;
        }

        /**
         * Selects all distinct lemmas or the best-weight analysis lemma.
         *
         * @param lemmaSelectionPolicy policy; default {@link MystemLemmaSelectionPolicy#ALL}
         * @return this builder
         * @throws NullPointerException if {@code lemmaSelectionPolicy} is {@code null}
         */
        public Builder lemmaSelectionPolicy(MystemLemmaSelectionPolicy lemmaSelectionPolicy) {
            this.lemmaSelectionPolicy = Objects.requireNonNull(lemmaSelectionPolicy, "lemmaSelectionPolicy");
            return this;
        }

        /**
         * Validates the policy combination and creates an immutable snapshot.
         *
         * @return options independent of later builder changes
         * @throws IllegalArgumentException if currency expansion is enabled without currency classification
         */
        public MystemSearchTokenizerOptions build() {
            return new MystemSearchTokenizerOptions(this);
        }
    }

}
