package io.github.ulviar.mystem4j.lucene;

import java.util.Objects;

/**
 * Immutable limits and policies for Lucene fields, MyStem requests, and token positions.
 *
 * <p>Limits count UTF-16 code units after Lucene character filtering and before MyStem Unicode
 * preparation, not bytes or code points. Truncation limits the indexed prefix but still reads the
 * remaining field to calculate its final offset. Chunking prefers whitespace boundaries; splitting a
 * longer run can change morphology because MyStem receives separate requests.
 *
 * <p>Use {@link #defaults()} for a bounded conservative configuration or {@link #builder()} to select
 * policies explicitly. Instances can be shared between threads.
 */
public final class MystemLuceneAnalysisOptions {
    /** Default analyzed field limit: 1,000,000 UTF-16 code units after character filtering. */
    public static final int DEFAULT_MAX_INPUT_CHARS = 1_000_000;
    /** Default request chunk limit: 32,768 UTF-16 code units before MyStem Unicode preparation. */
    public static final int DEFAULT_MAX_CHUNK_CHARS = 32_768;

    private final int maxInputChars;
    private final int maxChunkChars;
    private final MystemLucenePositionPolicy positionPolicy;
    private final MystemLuceneClientPolicy clientPolicy;
    private final MystemLuceneOversizedInputPolicy oversizedInputPolicy;

    private MystemLuceneAnalysisOptions(Builder builder) {
        maxInputChars = builder.maxInputChars;
        maxChunkChars = builder.maxChunkCharsSet
                ? builder.maxChunkChars
                : Math.min(DEFAULT_MAX_CHUNK_CHARS, builder.maxInputChars);
        positionPolicy = Objects.requireNonNull(builder.positionPolicy, "positionPolicy");
        clientPolicy = Objects.requireNonNull(builder.clientPolicy, "clientPolicy");
        oversizedInputPolicy = Objects.requireNonNull(builder.oversizedInputPolicy, "oversizedInputPolicy");
        if (maxInputChars <= 0) {
            throw new IllegalArgumentException("maxInputChars must be positive");
        }
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("maxChunkChars must be positive");
        }
        if (maxChunkChars > maxInputChars) {
            throw new IllegalArgumentException("maxChunkChars must not exceed maxInputChars");
        }
    }

    /**
     * Returns the maximum field length accepted for analysis before rejection or truncation.
     *
     * @return positive limit in UTF-16 code units after Lucene character filtering
     */
    public int maxInputChars() {
        return maxInputChars;
    }

    /**
     * Returns the preferred maximum size of one MyStem request before Unicode preparation.
     *
     * <p>A limit of one permits a complete two-unit surrogate pair when the field limit allows it.
     * Otherwise request boundaries stay within this limit and never split a valid pair.
     *
     * @return positive UTF-16 chunk limit, no greater than {@link #maxInputChars()}
     */
    public int maxChunkChars() {
        return maxChunkChars;
    }

    /**
     * Returns how skipped separator and other tokens affect phrase positions.
     *
     * @return non-null position policy
     */
    public MystemLucenePositionPolicy positionPolicy() {
        return positionPolicy;
    }

    /**
     * Returns how known client execution profiles are treated at analyzer or tokenizer construction.
     *
     * @return non-null client policy
     */
    public MystemLuceneClientPolicy clientPolicy() {
        return clientPolicy;
    }

    /**
     * Returns whether a field exceeding {@link #maxInputChars()} is rejected or truncated.
     *
     * @return non-null oversized-input policy
     */
    public MystemLuceneOversizedInputPolicy oversizedInputPolicy() {
        return oversizedInputPolicy;
    }

    /**
     * Returns a new builder initialized to {@link #defaults()} values.
     *
     * @return independent mutable builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder initialized from this option set.
     *
     * <p>The copied chunk size is explicit. If the field limit is reduced below it, also reduce the
     * chunk size before calling {@link Builder#build()}.
     *
     * @return independent mutable builder preserving all current values
     */
    public Builder toBuilder() {
        return builder()
                .maxInputChars(maxInputChars)
                .maxChunkChars(maxChunkChars)
                .positionPolicy(positionPolicy)
                .clientPolicy(clientPolicy)
                .oversizedInputPolicy(oversizedInputPolicy);
    }

    /**
     * Returns limits of 1,000,000 UTF-16 units per field and 32,768 per chunk, compact positions,
     * warnings for known slow clients, and failure when the field limit is exceeded.
     *
     * @return default Lucene analysis options
     */
    public static MystemLuceneAnalysisOptions defaults() {
        return builder().build();
    }

    /**
     * Returns options with a custom field limit and bounded default chunk size.
     *
     * <p>The chunk size is the smaller of {@link #DEFAULT_MAX_CHUNK_CHARS} and the supplied field limit.
     * Other policies retain their defaults.
     *
     * @param maxInputChars positive maximum analyzed length after character filtering, in UTF-16 code units
     * @return Lucene analysis options
     * @throws IllegalArgumentException if {@code maxInputChars} is not positive
     */
    public static MystemLuceneAnalysisOptions withMaxInputChars(int maxInputChars) {
        return builder().maxInputChars(maxInputChars).build();
    }

    /**
     * Builds immutable analysis options from {@link MystemLuceneAnalysisOptions#defaults()} values.
     *
     * <p>This mutable builder is intended for one thread. Built instances are unaffected by subsequent
     * changes. Numeric limits are validated together by {@link #build()}.
     */
    public static final class Builder {
        private int maxInputChars = DEFAULT_MAX_INPUT_CHARS;
        private int maxChunkChars = DEFAULT_MAX_CHUNK_CHARS;
        private boolean maxChunkCharsSet;
        private MystemLucenePositionPolicy positionPolicy = MystemLucenePositionPolicy.COMPACT;
        private MystemLuceneClientPolicy clientPolicy = MystemLuceneClientPolicy.WARN_ON_KNOWN_SLOW_CLIENTS;
        private MystemLuceneOversizedInputPolicy oversizedInputPolicy = MystemLuceneOversizedInputPolicy.FAIL;

        private Builder() {}

        /**
         * Sets the maximum analyzed field length after Lucene character filtering.
         *
         * <p>Unless {@link #maxChunkChars(int)} was called, the chunk size is reduced to fit this limit.
         * Under truncation the remainder is still read to establish the full field's final offset.
         *
         * @param maxInputChars positive UTF-16 limit; default {@link #DEFAULT_MAX_INPUT_CHARS}
         * @return this builder
         */
        public Builder maxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
            return this;
        }

        /**
         * Sets an explicit request chunk size before MyStem Unicode preparation.
         *
         * <p>Boundaries prefer whitespace and preserve valid surrogate pairs. A limit of one can
         * produce a two-unit request for a complete pair, subject to the field limit.
         *
         * @param maxChunkChars positive UTF-16 limit, no greater than the field limit
         * @return this builder
         */
        public Builder maxChunkChars(int maxChunkChars) {
            this.maxChunkChars = maxChunkChars;
            maxChunkCharsSet = true;
            return this;
        }

        /**
         * Selects how skipped source tokens affect phrase and proximity positions.
         *
         * @param positionPolicy policy; default {@link MystemLucenePositionPolicy#COMPACT}
         * @return this builder
         * @throws NullPointerException if {@code positionPolicy} is {@code null}
         */
        public Builder positionPolicy(MystemLucenePositionPolicy positionPolicy) {
            this.positionPolicy = Objects.requireNonNull(positionPolicy, "positionPolicy");
            return this;
        }

        /**
         * Selects whether known slow or serialized client profiles are allowed, warned about, or rejected.
         *
         * @param clientPolicy policy; default {@link MystemLuceneClientPolicy#WARN_ON_KNOWN_SLOW_CLIENTS}
         * @return this builder
         * @throws NullPointerException if {@code clientPolicy} is {@code null}
         */
        public Builder clientPolicy(MystemLuceneClientPolicy clientPolicy) {
            this.clientPolicy = Objects.requireNonNull(clientPolicy, "clientPolicy");
            return this;
        }

        /**
         * Selects rejection or prefix truncation when a field exceeds its limit.
         *
         * @param oversizedInputPolicy policy; default {@link MystemLuceneOversizedInputPolicy#FAIL}
         * @return this builder
         * @throws NullPointerException if {@code oversizedInputPolicy} is {@code null}
         */
        public Builder oversizedInputPolicy(MystemLuceneOversizedInputPolicy oversizedInputPolicy) {
            this.oversizedInputPolicy = Objects.requireNonNull(oversizedInputPolicy, "oversizedInputPolicy");
            return this;
        }

        /**
         * Validates both limits and creates an immutable snapshot.
         *
         * @return options independent of later builder changes
         * @throws IllegalArgumentException if either limit is not positive or the chunk limit exceeds the field limit
         */
        public MystemLuceneAnalysisOptions build() {
            return new MystemLuceneAnalysisOptions(this);
        }
    }

}
