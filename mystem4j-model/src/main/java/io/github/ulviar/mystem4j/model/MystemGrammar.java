package io.github.ulviar.mystem4j.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed view of a MyStem grammar string.
 */
public record MystemGrammar(
        String raw,
        Optional<String> partOfSpeech,
        Set<String> commonGrammemes,
        List<MystemGrammarVariant> variants) {
    public MystemGrammar {
        raw = Objects.requireNonNull(raw, "raw");
        partOfSpeech = Objects.requireNonNull(partOfSpeech, "partOfSpeech");
        commonGrammemes = Set.copyOf(Objects.requireNonNull(commonGrammemes, "commonGrammemes"));
        variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
    }

    public Set<String> allGrammemes() {
        LinkedHashSet<String> result = new LinkedHashSet<>(commonGrammemes);
        for (MystemGrammarVariant variant : variants) {
            result.addAll(variant.grammemes());
        }
        return Set.copyOf(result);
    }
}
