package io.github.ulviar.mystem4j.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed view of a MyStem grammar string.
 *
 * @param raw unmodified grammar string
 * @param partOfSpeech first item before {@code =}, or empty when no such item exists
 * @param commonGrammemes remaining items before {@code =}, copied into an immutable set
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
