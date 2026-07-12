package io.github.ulviar.mystem4j.lucene;

import java.util.Objects;

/**
 * Controls Lucene-side MyStem analysis limits, client policy, and token positions.
 *
 * <p>Create instances with {@link #builder()} so additional analysis controls do not change a positional constructor.
 */
public final class MystemLuceneAnalysisOptions {
    public static final int DEFAULT_MAX_INPUT_CHARS = 1_000_000;
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

    public int maxInputChars() {
        return maxInputChars;
    }

    public int maxChunkChars() {
        return maxChunkChars;
    }

    public MystemLucenePositionPolicy positionPolicy() {
        return positionPolicy;
    }

    public MystemLuceneClientPolicy clientPolicy() {
        return clientPolicy;
    }

    public MystemLuceneOversizedInputPolicy oversizedInputPolicy() {
        return oversizedInputPolicy;
    }

    /**
     * Returns a builder with conservative defaults.
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
                .maxInputChars(maxInputChars)
                .maxChunkChars(maxChunkChars)
                .positionPolicy(positionPolicy)
                .clientPolicy(clientPolicy)
                .oversizedInputPolicy(oversizedInputPolicy);
    }

    /**
     * Returns conservative defaults compatible with previous Lucene behavior.
     *
     * @return default Lucene analysis options
     */
    public static MystemLuceneAnalysisOptions defaults() {
        return builder().build();
    }

    /**
     * Returns options with a custom field limit and bounded default chunk size.
     *
     * @param maxInputChars maximum number of UTF-16 code units read from one Lucene field
     * @return Lucene analysis options
     */
    public static MystemLuceneAnalysisOptions withMaxInputChars(int maxInputChars) {
        return builder().maxInputChars(maxInputChars).build();
    }

    public static final class Builder {
        private int maxInputChars = DEFAULT_MAX_INPUT_CHARS;
        private int maxChunkChars = DEFAULT_MAX_CHUNK_CHARS;
        private boolean maxChunkCharsSet;
        private MystemLucenePositionPolicy positionPolicy = MystemLucenePositionPolicy.COMPACT;
        private MystemLuceneClientPolicy clientPolicy = MystemLuceneClientPolicy.WARN_ON_KNOWN_SLOW_CLIENTS;
        private MystemLuceneOversizedInputPolicy oversizedInputPolicy = MystemLuceneOversizedInputPolicy.FAIL;

        private Builder() {}

        public Builder maxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
            return this;
        }

        public Builder maxChunkChars(int maxChunkChars) {
            this.maxChunkChars = maxChunkChars;
            maxChunkCharsSet = true;
            return this;
        }

        public Builder positionPolicy(MystemLucenePositionPolicy positionPolicy) {
            this.positionPolicy = Objects.requireNonNull(positionPolicy, "positionPolicy");
            return this;
        }

        public Builder clientPolicy(MystemLuceneClientPolicy clientPolicy) {
            this.clientPolicy = Objects.requireNonNull(clientPolicy, "clientPolicy");
            return this;
        }

        public Builder oversizedInputPolicy(MystemLuceneOversizedInputPolicy oversizedInputPolicy) {
            this.oversizedInputPolicy = Objects.requireNonNull(oversizedInputPolicy, "oversizedInputPolicy");
            return this;
        }

        public MystemLuceneAnalysisOptions build() {
            return new MystemLuceneAnalysisOptions(this);
        }
    }

}
