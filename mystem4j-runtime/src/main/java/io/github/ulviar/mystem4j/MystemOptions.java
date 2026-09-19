package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable MyStem CLI options, created with {@link #builder()}.
 *
 * <p>Defaults are JSON output, UTF-8 encoding, no grammar filter or fixlist, and all boolean flags disabled.
 * Reusable and pooled clients additionally require JSON and {@code newLineEachWord(false)}. Building options
 * validates flag dependencies; building a client checks mode restrictions and fixlist readability.
 */
public final class MystemOptions {
    private final boolean newLineEachWord;
    private final boolean copyInput;
    private final boolean dictionaryWordsOnly;
    private final boolean lemmaOnly;
    private final boolean grammarInfo;
    private final boolean mergeWordForms;
    private final boolean sentenceMarkers;
    private final MystemEncoding encoding;
    private final boolean disambiguate;
    private final boolean englishGrammemes;
    private final Optional<String> filterGrammar;
    private final Optional<Path> fixlist;
    private final MystemOutputFormat format;
    private final boolean generateAll;
    private final boolean weight;

    private MystemOptions(Builder builder) {
        newLineEachWord = builder.newLineEachWord;
        copyInput = builder.copyInput;
        dictionaryWordsOnly = builder.dictionaryWordsOnly;
        lemmaOnly = builder.lemmaOnly;
        grammarInfo = builder.grammarInfo;
        mergeWordForms = builder.mergeWordForms;
        sentenceMarkers = builder.sentenceMarkers;
        encoding = Objects.requireNonNull(builder.encoding, "encoding");
        disambiguate = builder.disambiguate;
        englishGrammemes = builder.englishGrammemes;
        filterGrammar = Objects.requireNonNull(builder.filterGrammar, "filterGrammar");
        fixlist = Objects.requireNonNull(builder.fixlist, "fixlist");
        format = Objects.requireNonNull(builder.format, "format");
        generateAll = builder.generateAll;
        weight = builder.weight;
        if (mergeWordForms && !grammarInfo) {
            throw new MystemInvalidOptionsException("mergeWordForms requires grammarInfo.");
        }
        if (sentenceMarkers && !copyInput) {
            throw new MystemInvalidOptionsException("sentenceMarkers requires copyInput.");
        }
        filterGrammar.ifPresent(value -> {
            if (value.isBlank()) {
                throw new MystemInvalidOptionsException("filterGrammar must not be blank.");
            }
        });
    }

    /**
     * Prints each word on a separate line ({@code -n}); supported only by one-shot clients.
     *
     * @return whether per-word line output is enabled
     */
    public boolean newLineEachWord() {
        return newLineEachWord;
    }

    /**
     * Copies input text into MyStem output ({@code -c}).
     *
     * @return whether input copying is enabled
     */
    public boolean copyInput() {
        return copyInput;
    }

    /**
     * Restricts output to dictionary words ({@code -w}).
     *
     * @return whether only dictionary words are emitted
     */
    public boolean dictionaryWordsOnly() {
        return dictionaryWordsOnly;
    }

    /**
     * Omits original word forms from output ({@code -l}).
     *
     * @return whether only lemmas are emitted
     */
    public boolean lemmaOnly() {
        return lemmaOnly;
    }

    /**
     * Includes grammar information ({@code -i}).
     *
     * @return whether grammar information is emitted
     */
    public boolean grammarInfo() {
        return grammarInfo;
    }

    /**
     * Merges word forms ({@code -g}); requires {@link #grammarInfo()}.
     *
     * @return whether word-form merging is enabled
     */
    public boolean mergeWordForms() {
        return mergeWordForms;
    }

    /**
     * Includes sentence markers ({@code -s}); requires {@link #copyInput()}.
     *
     * @return whether sentence markers are emitted
     */
    public boolean sentenceMarkers() {
        return sentenceMarkers;
    }

    /**
     * Returns the charset used for process input and output.
     *
     * @return configured encoding; defaults to {@link MystemEncoding#UTF_8}
     */
    public MystemEncoding encoding() {
        return encoding;
    }

    /**
     * Enables contextual disambiguation ({@code -d}).
     *
     * @return whether disambiguation is enabled
     */
    public boolean disambiguate() {
        return disambiguate;
    }

    /**
     * Uses English grammar tag names ({@code --eng-gr}).
     *
     * @return whether English grammar names are requested
     */
    public boolean englishGrammemes() {
        return englishGrammemes;
    }

    /**
     * Returns the grammar filter passed to {@code --filter-gram}.
     *
     * @return non-blank filter, or empty when no filter is configured
     */
    public Optional<String> filterGrammar() {
        return filterGrammar;
    }

    /**
     * Returns the custom dictionary path passed to {@code --fixlist}.
     *
     * @return configured path, or empty when no fixlist is configured
     */
    public Optional<Path> fixlist() {
        return fixlist;
    }

    /**
     * Returns the output format passed to {@code --format}.
     *
     * @return configured format; defaults to {@link MystemOutputFormat#JSON}
     */
    public MystemOutputFormat format() {
        return format;
    }

    /**
     * Generates all hypotheses ({@code --generate-all}).
     *
     * @return whether all hypotheses are requested
     */
    public boolean generateAll() {
        return generateAll;
    }

    /**
     * Includes lemma probabilities ({@code --weight}).
     *
     * @return whether probabilities are requested
     */
    public boolean weight() {
        return weight;
    }

    /**
     * Creates a builder with all flags disabled, JSON output, and UTF-8 encoding.
     *
     * @return a new options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    List<String> toArguments() {
        ArrayList<String> arguments = new ArrayList<>();
        if (newLineEachWord) {
            arguments.add("-n");
        }
        if (copyInput) {
            arguments.add("-c");
        }
        if (dictionaryWordsOnly) {
            arguments.add("-w");
        }
        if (lemmaOnly) {
            arguments.add("-l");
        }
        if (grammarInfo) {
            arguments.add("-i");
        }
        if (mergeWordForms) {
            arguments.add("-g");
        }
        if (sentenceMarkers) {
            arguments.add("-s");
        }
        arguments.add("-e");
        arguments.add(encoding.cliName());
        if (disambiguate) {
            arguments.add("-d");
        }
        if (englishGrammemes) {
            arguments.add("--eng-gr");
        }
        filterGrammar.ifPresent(value -> {
            arguments.add("--filter-gram");
            arguments.add(value);
        });
        fixlist.ifPresent(path -> {
            arguments.add("--fixlist");
            arguments.add(path.toString());
        });
        arguments.add("--format");
        arguments.add(format.cliName());
        if (generateAll) {
            arguments.add("--generate-all");
        }
        if (weight) {
            arguments.add("--weight");
        }
        return List.copyOf(arguments);
    }

    /**
     * Mutable options builder. Instances are not intended for concurrent configuration.
     */
    public static final class Builder {
        private boolean newLineEachWord;
        private boolean copyInput;
        private boolean dictionaryWordsOnly;
        private boolean lemmaOnly;
        private boolean grammarInfo;
        private boolean mergeWordForms;
        private boolean sentenceMarkers;
        private MystemEncoding encoding = MystemEncoding.UTF_8;
        private boolean disambiguate;
        private boolean englishGrammemes;
        private Optional<String> filterGrammar = Optional.empty();
        private Optional<Path> fixlist = Optional.empty();
        private MystemOutputFormat format = MystemOutputFormat.JSON;
        private boolean generateAll;
        private boolean weight;

        private Builder() {}

        /**
         * Prints each word on a separate line ({@code -n}); supported only by one-shot clients. Default: {@code false}.
         *
         * @param newLineEachWord whether per-word line output is enabled
         * @return this builder
         */
        public Builder newLineEachWord(boolean newLineEachWord) {
            this.newLineEachWord = newLineEachWord;
            return this;
        }

        /**
         * Copies input text into MyStem output ({@code -c}). Default: {@code false}.
         *
         * @param copyInput whether input copying is enabled
         * @return this builder
         */
        public Builder copyInput(boolean copyInput) {
            this.copyInput = copyInput;
            return this;
        }

        /**
         * Restricts output to dictionary words ({@code -w}). Default: {@code false}.
         *
         * @param dictionaryWordsOnly whether only dictionary words are emitted
         * @return this builder
         */
        public Builder dictionaryWordsOnly(boolean dictionaryWordsOnly) {
            this.dictionaryWordsOnly = dictionaryWordsOnly;
            return this;
        }

        /**
         * Omits original word forms from output ({@code -l}). Default: {@code false}.
         *
         * @param lemmaOnly whether only lemmas are emitted
         * @return this builder
         */
        public Builder lemmaOnly(boolean lemmaOnly) {
            this.lemmaOnly = lemmaOnly;
            return this;
        }

        /**
         * Includes grammar information ({@code -i}). Default: {@code false}.
         *
         * @param grammarInfo whether grammar information is emitted
         * @return this builder
         */
        public Builder grammarInfo(boolean grammarInfo) {
            this.grammarInfo = grammarInfo;
            return this;
        }

        /**
         * Merges word forms ({@code -g}); requires {@link #grammarInfo(boolean) grammarInfo(true)}. Default: {@code false}.
         *
         * @param mergeWordForms whether word-form merging is enabled
         * @return this builder
         */
        public Builder mergeWordForms(boolean mergeWordForms) {
            this.mergeWordForms = mergeWordForms;
            return this;
        }

        /**
         * Includes sentence markers ({@code -s}); requires {@link #copyInput(boolean) copyInput(true)}. Default: {@code false}.
         *
         * @param sentenceMarkers whether sentence markers are emitted
         * @return this builder
         */
        public Builder sentenceMarkers(boolean sentenceMarkers) {
            this.sentenceMarkers = sentenceMarkers;
            return this;
        }

        /**
         * Sets the process encoding ({@code -e}); defaults to UTF-8.
         *
         * @param encoding non-null input/output encoding
         * @return this builder
         */
        public Builder encoding(MystemEncoding encoding) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            return this;
        }

        /**
         * Enables contextual disambiguation ({@code -d}). Default: {@code false}.
         *
         * @param disambiguate whether disambiguation is enabled
         * @return this builder
         */
        public Builder disambiguate(boolean disambiguate) {
            this.disambiguate = disambiguate;
            return this;
        }

        /**
         * Uses English grammar tag names ({@code --eng-gr}). Default: {@code false}.
         *
         * @param englishGrammemes whether English grammar names are requested
         * @return this builder
         */
        public Builder englishGrammemes(boolean englishGrammemes) {
            this.englishGrammemes = englishGrammemes;
            return this;
        }

        /**
         * Sets the grammar filter ({@code --filter-gram}); no filter is configured by default.
         *
         * @param filterGrammar non-null, non-blank expression; validated by {@link #build()}
         * @return this builder
         */
        public Builder filterGrammar(String filterGrammar) {
            this.filterGrammar = Optional.of(Objects.requireNonNull(filterGrammar, "filterGrammar"));
            return this;
        }

        /**
         * Sets the custom dictionary path ({@code --fixlist}); absent by default.
         *
         * <p>The path must name a readable regular file when a client is built.
         *
         * @param fixlist non-null custom dictionary path
         * @return this builder
         */
        public Builder fixlist(Path fixlist) {
            this.fixlist = Optional.of(Objects.requireNonNull(fixlist, "fixlist"));
            return this;
        }

        /**
         * Sets the output format; defaults to JSON.
         *
         * <p>Reusable and pooled clients reject XML and TEXT when built.
         *
         * @param format non-null MyStem output format
         * @return this builder
         */
        public Builder format(MystemOutputFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        /**
         * Generates all hypotheses ({@code --generate-all}). Default: {@code false}.
         *
         * @param generateAll whether all hypotheses are requested
         * @return this builder
         */
        public Builder generateAll(boolean generateAll) {
            this.generateAll = generateAll;
            return this;
        }

        /**
         * Includes lemma probabilities ({@code --weight}). Default: {@code false}.
         *
         * @param weight whether probabilities are requested
         * @return this builder
         */
        public Builder weight(boolean weight) {
            this.weight = weight;
            return this;
        }

        /**
         * Creates an immutable snapshot of these options.
         *
         * @return validated options
         * @throws MystemInvalidOptionsException when word-form merging lacks grammar information,
         *     sentence markers lack input copying, or a grammar filter is blank
         */
        public MystemOptions build() {
            return new MystemOptions(this);
        }
    }

}
