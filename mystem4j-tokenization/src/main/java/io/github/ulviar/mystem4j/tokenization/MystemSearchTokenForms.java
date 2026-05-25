package io.github.ulviar.mystem4j.tokenization;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MystemSearchTokenForms {
    private static final Set<Integer> EXCEPTIONAL_DIACRITICS = Set.of(0x0301, 0x0341);

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
            if (containsExceptionalDiacritics(value)) {
                expanded.add(removeExceptionalDiacritics(value));
            } else {
                expanded.add(value);
                suffixless(value, token.features).ifPresent(expanded::add);
            }
        }
        return toForms(expanded, keyword);
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
        ArrayList<MystemTokenForm> forms = new ArrayList<>();
        for (String value : values) {
            if (!value.isEmpty()) {
                forms.add(new MystemTokenForm(value.toLowerCase(Locale.ROOT), keyword));
            }
        }
        return List.copyOf(forms);
    }

    private static boolean containsExceptionalDiacritics(String text) {
        return text.codePoints().anyMatch(EXCEPTIONAL_DIACRITICS::contains);
    }

    private static String removeExceptionalDiacritics(String text) {
        int[] codePoints = text.codePoints()
                .filter(codePoint -> !EXCEPTIONAL_DIACRITICS.contains(codePoint))
                .toArray();
        return new String(codePoints, 0, codePoints.length);
    }
}
