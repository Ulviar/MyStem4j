package io.github.ulviar.mystem4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed representation of MyStem CLI options.
 */
public record MystemOptions(
        boolean newLineEachWord,
        boolean copyInput,
        boolean dictionaryWordsOnly,
        boolean lemmaOnly,
        boolean grammarInfo,
        boolean mergeWordForms,
        boolean sentenceMarkers,
        MystemEncoding encoding,
        boolean disambiguate,
        boolean englishGrammemes,
        Optional<String> filterGrammar,
        Optional<Path> fixlist,
        MystemOutputFormat format,
        boolean generateAll,
        boolean weight) {
    public MystemOptions {
        encoding = Objects.requireNonNull(encoding, "encoding");
        filterGrammar = Objects.requireNonNull(filterGrammar, "filterGrammar");
        fixlist = Objects.requireNonNull(fixlist, "fixlist");
        format = Objects.requireNonNull(format, "format");
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
        fixlist.ifPresent(path -> {
            if (!Files.isReadable(path)) {
                throw new MystemInvalidOptionsException("fixlist must be readable: " + path);
            }
        });
    }

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

        public Builder newLineEachWord(boolean newLineEachWord) {
            this.newLineEachWord = newLineEachWord;
            return this;
        }

        public Builder copyInput(boolean copyInput) {
            this.copyInput = copyInput;
            return this;
        }

        public Builder dictionaryWordsOnly(boolean dictionaryWordsOnly) {
            this.dictionaryWordsOnly = dictionaryWordsOnly;
            return this;
        }

        public Builder lemmaOnly(boolean lemmaOnly) {
            this.lemmaOnly = lemmaOnly;
            return this;
        }

        public Builder grammarInfo(boolean grammarInfo) {
            this.grammarInfo = grammarInfo;
            return this;
        }

        public Builder mergeWordForms(boolean mergeWordForms) {
            this.mergeWordForms = mergeWordForms;
            return this;
        }

        public Builder sentenceMarkers(boolean sentenceMarkers) {
            this.sentenceMarkers = sentenceMarkers;
            return this;
        }

        public Builder encoding(MystemEncoding encoding) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            return this;
        }

        public Builder disambiguate(boolean disambiguate) {
            this.disambiguate = disambiguate;
            return this;
        }

        public Builder englishGrammemes(boolean englishGrammemes) {
            this.englishGrammemes = englishGrammemes;
            return this;
        }

        public Builder filterGrammar(String filterGrammar) {
            this.filterGrammar = Optional.of(Objects.requireNonNull(filterGrammar, "filterGrammar"));
            return this;
        }

        public Builder fixlist(Path fixlist) {
            this.fixlist = Optional.of(Objects.requireNonNull(fixlist, "fixlist"));
            return this;
        }

        public Builder format(MystemOutputFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        public Builder generateAll(boolean generateAll) {
            this.generateAll = generateAll;
            return this;
        }

        public Builder weight(boolean weight) {
            this.weight = weight;
            return this;
        }

        public MystemOptions build() {
            return new MystemOptions(
                    newLineEachWord,
                    copyInput,
                    dictionaryWordsOnly,
                    lemmaOnly,
                    grammarInfo,
                    mergeWordForms,
                    sentenceMarkers,
                    encoding,
                    disambiguate,
                    englishGrammemes,
                    filterGrammar,
                    fixlist,
                    format,
                    generateAll,
                    weight);
        }
    }
}
