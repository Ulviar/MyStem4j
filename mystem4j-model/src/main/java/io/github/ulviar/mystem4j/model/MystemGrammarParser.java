package io.github.ulviar.mystem4j.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Splits MyStem {@code gr} strings into a part of speech, common grammemes, and inflection alternatives.
 *
 * <p>This parser performs structural splitting, not linguistic validation. It preserves unknown
 * tags and the raw input so that callers can interpret tags introduced by a newer MyStem version.
 * Its static method is safe to call concurrently.
 *
 * <pre>{@code
 * MystemGrammar grammar = MystemGrammarParser.parse("S,жен=(пр,ед|пр,мн)");
 * String partOfSpeech = grammar.partOfSpeech().orElseThrow(); // S
 * boolean feminine = grammar.commonGrammemes().contains("жен"); // true
 * int alternatives = grammar.variants().size(); // 2
 * }</pre>
 */
public final class MystemGrammarParser {
    private MystemGrammarParser() {}

    /**
     * Splits a MyStem grammar string into shared features and inflection alternatives.
     *
     * <p>The first nonempty comma-separated item before the first {@code =} is the part of speech.
     * Remaining items there are common grammemes; {@code |} separates alternatives after {@code =}.
     * Items are trimmed with {@link String#trim()}, empty items are ignored, and outer alternative
     * parentheses are removed. Duplicate grammemes within a set collapse to one value. Alternative
     * order is retained, including empty alternatives before or after a {@code |}; grammeme set
     * iteration order is unspecified.
     *
     * <p>A missing or blank right side produces one empty alternative. An empty or {@code null}
     * input therefore has no part of speech or common grammemes and has one empty alternative.
     * The returned {@link MystemGrammar#raw()} retains the input exactly, except that {@code null}
     * becomes an empty string. Unknown tags are preserved without a fixed-vocabulary check.
     *
     * @param grammar MyStem {@code gr} string; {@code null} is treated as an empty string
     * @return immutable parsed view retaining the raw string, or an empty raw string for {@code null}
     */
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
            for (String variant : parts[1].split("\\|", -1)) {
                variants.add(new MystemGrammarVariant(new LinkedHashSet<>(splitGrammemes(stripVariantBrackets(variant)))));
            }
        }
        return new MystemGrammar(raw, partOfSpeech, common, variants);
    }

    private static String stripVariantBrackets(String value) {
        int start = 0;
        int end = value.length();
        // String.trim() removes UTF-16 units <= U+0020, rather than all Unicode whitespace.
        while (start < end && (value.charAt(start) <= ' ' || value.charAt(start) == '(')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) <= ' ' || value.charAt(end - 1) == ')')) {
            end--;
        }
        return value.substring(start, end);
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
