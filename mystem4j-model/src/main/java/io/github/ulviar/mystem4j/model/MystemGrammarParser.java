package io.github.ulviar.mystem4j.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parser for MyStem grammar strings.
 */
public final class MystemGrammarParser {
    private MystemGrammarParser() {}

    public static MystemGrammar parse(String grammar) {
        String raw = grammar == null ? "" : grammar;
        String[] parts = raw.split("=", 2);
        List<String> left = splitGrammemes(parts[0]);
        Optional<String> partOfSpeech = left.isEmpty() ? Optional.empty() : Optional.of(left.get(0));
        Set<String> common = new LinkedHashSet<>();
        if (left.size() > 1) {
            common.addAll(left.subList(1, left.size()));
        }

        ArrayList<MystemGrammarVariant> variants = new ArrayList<>();
        if (parts.length == 1 || parts[1].isBlank()) {
            variants.add(new MystemGrammarVariant(Set.of()));
        } else {
            for (String variant : parts[1].split("\\|")) {
                variants.add(new MystemGrammarVariant(new LinkedHashSet<>(splitGrammemes(stripVariantBrackets(variant)))));
            }
        }
        return new MystemGrammar(raw, partOfSpeech, common, variants);
    }

    private static String stripVariantBrackets(String value) {
        String result = value.trim();
        while (result.startsWith("(")) {
            result = result.substring(1).trim();
        }
        while (result.endsWith(")")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static List<String> splitGrammemes(String value) {
        ArrayList<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String normalized = part.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }
}
