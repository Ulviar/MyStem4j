package io.github.ulviar.mystem4j.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable view of the part of speech, shared features, and alternatives in a MyStem grammar string.
 *
 * <p>Use {@link MystemGrammarParser#parse(String)} to create a grammar from a {@code gr} field.
 * For {@code S,жен,од=им,ед}, the part of speech is {@code S}, common grammemes are {@code жен}
 * and {@code од}, and the single variant contains {@code им} and {@code ед}. The complete features
 * of a reading consist of the common grammemes plus one variant, with the part of speech stored
 * separately. Tags are strings: this model neither validates a fixed vocabulary nor translates
 * MyStem's tags.
 *
 * @param raw unmodified grammar string
 * @param partOfSpeech first item before {@code =}, or empty when no such item exists
 * @param commonGrammemes remaining items before {@code =}, copied into an immutable set with
 *     unspecified iteration order
 * @param variants alternatives after {@code =}, in source order and copied into an immutable list
 */
public record MystemGrammar(
        String raw,
        Optional<String> partOfSpeech,
        Set<String> commonGrammemes,
        List<MystemGrammarVariant> variants) {
    /**
     * Creates a grammar and copies its grammeme and variant collections.
     *
     * <p>This constructor does not parse {@code raw} or check that the supplied components agree
     * with it. It also permits an empty variant list. Use {@link MystemGrammarParser#parse(String)}
     * when the grammar string should determine the components.
     *
     * @param raw unmodified grammar string
     * @param partOfSpeech optional part-of-speech tag
     * @param commonGrammemes features shared by all alternatives
     * @param variants alternatives in source order
     * @throws NullPointerException if an argument or a collection element is {@code null}
     */
    public MystemGrammar {
        raw = Objects.requireNonNull(raw, "raw");
        partOfSpeech = Objects.requireNonNull(partOfSpeech, "partOfSpeech");
        commonGrammemes = Set.copyOf(Objects.requireNonNull(commonGrammemes, "commonGrammemes"));
        variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
    }

    /**
     * Collects shared features and features from every alternative.
     *
     * <p>The part-of-speech tag is excluded. The union loses the association between features and
     * alternatives: for example, both singular and plural can be present without describing a
     * single reading. Inspect {@link #variants()} when those associations matter.
     *
     * @return immutable union of grammemes, without a guaranteed iteration order
     */
    public Set<String> allGrammemes() {
        LinkedHashSet<String> result = new LinkedHashSet<>(commonGrammemes);
        for (MystemGrammarVariant variant : variants) {
            result.addAll(variant.grammemes());
        }
        return Set.copyOf(result);
    }
}
