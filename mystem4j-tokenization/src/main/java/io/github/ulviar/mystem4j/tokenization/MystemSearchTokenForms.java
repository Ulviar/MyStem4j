package io.github.ulviar.mystem4j.tokenization;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MystemSearchTokenForms {
    private MystemSearchTokenForms() {}

    static List<MystemTokenForm> forms(
            String sourceText,
            MystemPreparedSearchToken token,
            MystemSearchTokenType type,
            MystemSearchTokenizerOptions options) {
        return switch (type) {
            case URL, EMAIL -> keywordForms(sourceText, token.forms);
            case CURRENCY -> options.expandCurrencyForms()
                    ? currencyForms(sourceText)
                    : keywordForms(sourceText, List.of());
            case NUMBER -> wordForms(sourceText, token, true);
            case WORD -> wordForms(
                    sourceText,
                    token,
                    !token.lemmas.isEmpty() || token.features.contains(MystemTokenFeature.NUMBER));
            case SEPARATOR, OTHER -> keywordForms(sourceText, List.of());
        };
    }

    private static List<MystemTokenForm> wordForms(
            String sourceText, MystemPreparedSearchToken token, boolean keyword) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (token.lemmas.isEmpty()) {
            values.add(sourceText);
        } else {
            values.addAll(token.lemmas);
        }
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String value : values) {
            addNormalizedWordForms(expanded, value, token.features);
        }
        if (expanded.isEmpty()) {
            addNormalizedWordForms(expanded, sourceText, token.features);
            if (expanded.isEmpty()) {
                // A custom model may provide only removable marks as a lemma and source.
                // Keep its source token representable without manufacturing an empty term.
                expanded.add(sourceText);
            }
        }
        return toForms(expanded, keyword);
    }

    private static void addNormalizedWordForms(
            Set<String> forms, String value, EnumSet<MystemTokenFeature> features) {
        String normalized = normalizeWordForm(value);
        if (!normalized.isEmpty()) {
            forms.add(normalized);
            suffixless(normalized, features).filter(form -> !form.isEmpty()).ifPresent(forms::add);
        }
    }

    private static java.util.Optional<String> suffixless(String value, EnumSet<MystemTokenFeature> features) {
        if (features.contains(MystemTokenFeature.ENDS_WITH_DOUBLE_PLUSES) && value.endsWith("++")) {
            return java.util.Optional.of(value.substring(0, value.length() - 2));
        }
        if ((features.contains(MystemTokenFeature.ENDS_WITH_PLUS) && value.endsWith("+"))
                || (features.contains(MystemTokenFeature.ENDS_WITH_NUMBER_SIGN) && value.endsWith("#"))) {
            return java.util.Optional.of(value.substring(0, value.length() - 1));
        }
        return java.util.Optional.empty();
    }

    private static List<MystemTokenForm> keywordForms(String sourceText, List<String> extraForms) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(sourceText);
        values.addAll(extraForms);
        return toForms(values, true);
    }

    private static List<MystemTokenForm> currencyForms(String sourceText) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(sourceText);
        CurrencyData.forms(sourceText).forEach(values::add);
        return toForms(values, true);
    }

    private static List<MystemTokenForm> toForms(Set<String> values, boolean keyword) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String value : values) {
            if (!value.isEmpty()) {
                terms.add(value.toLowerCase(Locale.ROOT));
            }
        }
        // Keep literal forms first, especially full URL/email values and their domains.
        // Normalize the original values: lowercasing first loses the source casing needed
        // to agree with single-term query normalization (for example, Greek final sigma).
        for (String value : values) {
            String alias = MystemSearchTermNormalizer.normalize(value);
            if (!alias.isEmpty()) {
                terms.add(alias);
            }
        }
        ArrayList<MystemTokenForm> forms = new ArrayList<>(terms.size());
        terms.forEach(term -> forms.add(new MystemTokenForm(term, keyword)));
        return List.copyOf(forms);
    }

    private static String normalizeWordForm(String text) {
        StringBuilder normalized = null;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == MystemSearchTokenClassifier.SOFT_HYPHEN
                    || character == '\u0301'
                    || character == '\u0341') {
                if (normalized == null) {
                    normalized = new StringBuilder(text.length()).append(text, 0, index);
                }
            } else if (normalized != null) {
                normalized.append(character);
            }
        }
        return normalized == null ? text : normalized.toString();
    }
}
