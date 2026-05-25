package io.github.ulviar.mystem4j.tokenization;

import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts parsed MyStem tokens into search-oriented tokens.
 */
public final class MystemSearchTokenizer {
    private static final int SOFT_HYPHEN = 0x00AD;
    private static final int PLUS = '+';
    private static final int NUMBER_SIGN = '#';
    private static final int YUAN = '元';
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<Integer> EXCEPTIONAL_DIACRITICS = Set.of(0x0301, 0x0341);
    private final MystemSearchTokenizerOptions options;

    /**
     * Creates a tokenizer with conservative options.
     */
    public MystemSearchTokenizer() {
        this(MystemSearchTokenizerOptions.conservative());
    }

    /**
     * Creates a tokenizer with explicit tokenization options.
     *
     * @param options tokenization policy
     */
    public MystemSearchTokenizer(MystemSearchTokenizerOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Converts a parsed MyStem document to search tokens.
     *
     * <p>When MyStem omits non-copy input fragments, gaps are synthesized from the original document text.
     *
     * @param document parsed MyStem document
     * @return search tokens with original-text offsets
     */
    public List<MystemSearchToken> tokenize(MystemDocument document) {
        Objects.requireNonNull(document, "document");
        List<PreparedToken> preparedTokens = prepareTokens(document, options);
        List<PreparedToken> mergedTokens = mergeCompositeTokens(document.originalText(), preparedTokens, options);
        ArrayList<MystemSearchToken> result = new ArrayList<>(mergedTokens.size());
        for (PreparedToken token : mergedTokens) {
            result.add(toSearchToken(document.originalText(), token, options));
        }
        return List.copyOf(result);
    }

    private static List<PreparedToken> prepareTokens(MystemDocument document, MystemSearchTokenizerOptions options) {
        ArrayList<PreparedToken> result = new ArrayList<>(document.tokens().size());
        String originalText = document.originalText();
        int cursor = 0;
        for (MystemToken token : document.tokens()) {
            validateTokenRange(originalText, token);
            if (token.text().isEmpty()) {
                continue;
            }
            PreparedToken prepared = PreparedToken.from(token, options);
            if (prepared.startOffset < cursor) {
                prepared = relocateOverlappingToken(originalText, prepared, cursor);
                if (prepared == null) {
                    continue;
                }
            }
            if (prepared.startOffset > cursor) {
                appendGapTokens(originalText, cursor, prepared.startOffset, result, options);
                cursor = prepared.startOffset;
            }
            extendTokenForDroppedSuffixes(originalText, prepared);
            result.add(prepared);
            cursor = Math.max(cursor, prepared.endOffset);
        }
        if (cursor < originalText.length()) {
            appendGapTokens(originalText, cursor, originalText.length(), result, options);
        }
        return result;
    }

    private static void validateTokenRange(String originalText, MystemToken token) {
        if (!token.hasKnownOffsets()) {
            throw new MystemTokenizationException("Cannot tokenize MyStem token with unknown offsets: " + token);
        }
        if (token.endOffset() > originalText.length()) {
            throw new MystemTokenizationException("MyStem token offsets exceed original text length: " + token);
        }
    }

    private static void appendGapTokens(
            String originalText,
            int startOffset,
            int endOffset,
            List<PreparedToken> result,
            MystemSearchTokenizerOptions options) {
        int index = startOffset;
        while (index < endOffset) {
            int nextOffset = nextGapTokenEnd(originalText, index, endOffset, options);
            String text = originalText.substring(index, nextOffset);
            result.add(PreparedToken.gap(text, index, nextOffset, options));
            index = nextOffset;
        }
    }

    private static int nextGapTokenEnd(
            String originalText, int startOffset, int limit, MystemSearchTokenizerOptions options) {
        if (startsWith(originalText, startOffset, limit, "://")) {
            return startOffset + 3;
        }
        int codePoint = originalText.codePointAt(startOffset);
        if (codePoint == '@'
                || (options.classifyCurrencies() && isCurrencySymbol(Character.toString(codePoint)))) {
            return startOffset + Character.charCount(codePoint);
        }
        if (isSeparator(codePoint)) {
            return runEnd(originalText, startOffset, limit, MystemSearchTokenizer::isSeparator);
        }
        if (isNumberPart(codePoint)) {
            return runEnd(originalText, startOffset, limit, MystemSearchTokenizer::isNumberPart);
        }
        if (isWordPart(codePoint)) {
            return runEnd(originalText, startOffset, limit, MystemSearchTokenizer::isWordPart);
        }
        return startOffset + Character.charCount(codePoint);
    }

    private static boolean startsWith(String text, int startOffset, int limit, String prefix) {
        return startOffset + prefix.length() <= limit && text.startsWith(prefix, startOffset);
    }

    private static int runEnd(String text, int startOffset, int limit, CodePointPredicate predicate) {
        int index = startOffset;
        while (index < limit) {
            int codePoint = text.codePointAt(index);
            if (!predicate.test(codePoint) || codePoint == '@' || startsWith(text, index, limit, "://")) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index == startOffset ? startOffset + Character.charCount(text.codePointAt(startOffset)) : index;
    }

    private static PreparedToken relocateOverlappingToken(String originalText, PreparedToken token, int cursor) {
        int relocated = originalText.indexOf(token.text, cursor);
        if (relocated < 0) {
            return token.endOffset <= cursor ? null : token.withOffsets(cursor, token.endOffset);
        }
        return token.withOffsets(relocated, relocated + token.text.length());
    }

    private static void extendTokenForDroppedSuffixes(String originalText, PreparedToken token) {
        if (!token.features.contains(TokenFeature.WORD)) {
            return;
        }
        while (token.endOffset < originalText.length()
                && originalText.codePointAt(token.endOffset) == SOFT_HYPHEN) {
            token.endOffset += Character.charCount(SOFT_HYPHEN);
        }
        if (token.endOffset >= originalText.length()) {
            return;
        }
        int codePoint = originalText.codePointAt(token.endOffset);
        if (codePoint == NUMBER_SIGN) {
            token.features.add(TokenFeature.ENDS_WITH_NUMBER_SIGN);
            token.endOffset += Character.charCount(NUMBER_SIGN);
        } else if (codePoint == PLUS) {
            token.endOffset += Character.charCount(PLUS);
            if (token.endOffset < originalText.length() && originalText.codePointAt(token.endOffset) == PLUS) {
                token.features.add(TokenFeature.ENDS_WITH_DOUBLE_PLUSES);
                token.endOffset += Character.charCount(PLUS);
            } else {
                token.features.add(TokenFeature.ENDS_WITH_PLUS);
            }
        }
    }

    private static List<PreparedToken> mergeCompositeTokens(
            String originalText, List<PreparedToken> tokens, MystemSearchTokenizerOptions options) {
        if (!options.mergeUrls() && !options.mergeEmails()) {
            return List.copyOf(tokens);
        }
        ArrayList<PreparedToken> result = new ArrayList<>();
        ArrayList<PreparedToken> group = new ArrayList<>();
        boolean possibleUrl = false;
        boolean possibleEmail = false;
        for (PreparedToken token : tokens) {
            possibleUrl = possibleUrl || (options.mergeUrls() && token.features.contains(TokenFeature.URL_PART));
            possibleEmail = possibleEmail || (options.mergeEmails() && token.features.contains(TokenFeature.EMAIL_PART));
            if (token.features.contains(TokenFeature.SEPARATOR)) {
                flushGroup(originalText, group, possibleUrl, possibleEmail, result);
                group.clear();
                possibleUrl = false;
                possibleEmail = false;
                result.add(token);
            } else {
                group.add(token);
            }
        }
        flushGroup(originalText, group, possibleUrl, possibleEmail, result);
        return List.copyOf(result);
    }

    private static void flushGroup(
            String originalText,
            List<PreparedToken> group,
            boolean possibleUrl,
            boolean possibleEmail,
            List<PreparedToken> result) {
        if (group.isEmpty()) {
            return;
        }
        if (possibleUrl == possibleEmail) {
            result.addAll(group);
            return;
        }
        List<PreparedToken> merged = possibleUrl ? mergeUrl(originalText, group) : mergeEmail(originalText, group);
        if (merged.isEmpty()) {
            result.addAll(group);
        } else {
            result.addAll(merged);
        }
    }

    private static List<PreparedToken> mergeUrl(String originalText, List<PreparedToken> group) {
        MergeRange range = mergeRange(group);
        if (range == null) {
            return List.of();
        }
        String text = originalText.substring(range.startOffset(), range.endOffset());
        String host = urlHost(text);
        if (host == null) {
            return List.of();
        }
        PreparedToken token = PreparedToken.composite(text, range.startOffset(), range.endOffset(), TokenFeature.URL);
        token.forms.add(host);
        return mergedGroup(group, range, token);
    }

    private static List<PreparedToken> mergeEmail(String originalText, List<PreparedToken> group) {
        MergeRange range = mergeRange(group);
        if (range == null) {
            return List.of();
        }
        String text = originalText.substring(range.startOffset(), range.endOffset());
        if (!EMAIL_PATTERN.matcher(text).matches()) {
            return List.of();
        }
        String domain = text.substring(text.lastIndexOf('@') + 1);
        PreparedToken token = PreparedToken.composite(text, range.startOffset(), range.endOffset(), TokenFeature.EMAIL);
        token.forms.add(domain);
        return mergedGroup(group, range, token);
    }

    private static List<PreparedToken> mergedGroup(List<PreparedToken> group, MergeRange range, PreparedToken token) {
        ArrayList<PreparedToken> result = new ArrayList<>(group.size() - range.lastIndex() + range.firstIndex() + 1);
        result.addAll(group.subList(0, range.firstIndex()));
        result.add(token);
        result.addAll(group.subList(range.lastIndex() + 1, group.size()));
        return List.copyOf(result);
    }

    private static MergeRange mergeRange(List<PreparedToken> group) {
        int first = -1;
        int last = -1;
        for (int index = 0; index < group.size(); index++) {
            if (group.get(index).features.contains(TokenFeature.WORD)
                    || group.get(index).features.contains(TokenFeature.NUMBER)) {
                if (first < 0) {
                    first = index;
                }
                last = index;
            }
        }
        if (first < 0) {
            return null;
        }
        return new MergeRange(first, last, group.get(first).startOffset, group.get(last).endOffset);
    }

    private static String urlHost(String text) {
        try {
            URI uri = new URI(text);
            String host = uri.getHost();
            return host == null || host.isBlank() ? null : host;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private static MystemSearchToken toSearchToken(
            String originalText, PreparedToken token, MystemSearchTokenizerOptions options) {
        String sourceText = originalText.substring(token.startOffset, token.endOffset);
        MystemSearchTokenType type = tokenType(token, options);
        return new MystemSearchToken(
                sourceText, forms(sourceText, token, type, options), token.startOffset, token.endOffset, type);
    }

    private static MystemSearchTokenType tokenType(PreparedToken token, MystemSearchTokenizerOptions options) {
        if (token.features.contains(TokenFeature.URL)) {
            return MystemSearchTokenType.URL;
        }
        if (token.features.contains(TokenFeature.EMAIL)) {
            return MystemSearchTokenType.EMAIL;
        }
        if (token.features.contains(TokenFeature.CURRENCY)) {
            return MystemSearchTokenType.CURRENCY;
        }
        if (options.classifyNumbers() && token.features.contains(TokenFeature.NUMBER)) {
            return MystemSearchTokenType.NUMBER;
        }
        if (token.features.contains(TokenFeature.WORD)) {
            return MystemSearchTokenType.WORD;
        }
        if (token.features.contains(TokenFeature.SEPARATOR)) {
            return MystemSearchTokenType.SEPARATOR;
        }
        return MystemSearchTokenType.OTHER;
    }

    private static List<MystemTokenForm> forms(
            String sourceText, PreparedToken token, MystemSearchTokenType type, MystemSearchTokenizerOptions options) {
        return switch (type) {
            case URL, EMAIL -> keywordForms(sourceText, token.forms);
            case CURRENCY -> options.expandCurrencyForms() ? currencyForms(sourceText) : keywordForms(sourceText, List.of());
            case NUMBER -> wordForms(sourceText, token, true);
            case WORD -> wordForms(sourceText, token, !token.lemmas.isEmpty() || token.features.contains(TokenFeature.NUMBER));
            case SEPARATOR, OTHER -> keywordForms(sourceText, List.of());
        };
    }

    private static List<MystemTokenForm> wordForms(String sourceText, PreparedToken token, boolean keyword) {
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

    private static java.util.Optional<String> suffixless(String value, EnumSet<TokenFeature> features) {
        if (features.contains(TokenFeature.ENDS_WITH_DOUBLE_PLUSES) && value.endsWith("++")) {
            return java.util.Optional.of(value.substring(0, value.length() - 2));
        }
        if ((features.contains(TokenFeature.ENDS_WITH_PLUS) && value.endsWith("+"))
                || (features.contains(TokenFeature.ENDS_WITH_NUMBER_SIGN) && value.endsWith("#"))) {
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
        int[] codePoints = text.codePoints().filter(codePoint -> !EXCEPTIONAL_DIACRITICS.contains(codePoint)).toArray();
        return new String(codePoints, 0, codePoints.length);
    }

    private static EnumSet<TokenFeature> detectFeatures(
            String text, List<String> lemmas, MystemSearchTokenizerOptions options) {
        if (!lemmas.isEmpty()) {
            return EnumSet.of(TokenFeature.WORD, TokenFeature.RUSSIAN_LEMMATIZED_WORD);
        }
        if (options.classifyCurrencies() && isCurrencySymbol(text)) {
            return EnumSet.of(TokenFeature.CURRENCY);
        }
        if (options.mergeEmails() && "@".equals(text)) {
            return EnumSet.of(TokenFeature.EMAIL_PART);
        }
        if (options.mergeUrls() && "://".equals(text)) {
            return EnumSet.of(TokenFeature.URL_PART);
        }
        EnumSet<TokenFeature> result = EnumSet.noneOf(TokenFeature.class);
        boolean allWordParts = true;
        boolean containsLetterOrDigit = false;
        boolean allNumberParts = true;
        boolean containsDigit = false;
        boolean containsSeparator = false;
        int[] codePoints = text.codePoints().toArray();
        for (int codePoint : codePoints) {
            allWordParts = allWordParts && isWordPart(codePoint);
            containsLetterOrDigit = containsLetterOrDigit || Character.isLetterOrDigit(codePoint);
            allNumberParts = allNumberParts && isNumberPart(codePoint);
            containsDigit = containsDigit || Character.isDigit(codePoint);
            containsSeparator = containsSeparator || isSeparator(codePoint);
        }
        if (containsSeparator) {
            result.add(TokenFeature.SEPARATOR);
        }
        if (allNumberParts && containsDigit) {
            result.add(TokenFeature.NUMBER);
            result.add(TokenFeature.WORD);
            addNumericSuffixFeatures(text, result);
        } else if (allWordParts && containsLetterOrDigit) {
            result.add(TokenFeature.WORD);
        }
        return result;
    }

    private static void addNumericSuffixFeatures(String text, EnumSet<TokenFeature> features) {
        if (text.endsWith("#")) {
            features.add(TokenFeature.ENDS_WITH_NUMBER_SIGN);
        } else if (text.endsWith("++")) {
            features.add(TokenFeature.ENDS_WITH_DOUBLE_PLUSES);
        } else if (text.endsWith("+")) {
            features.add(TokenFeature.ENDS_WITH_PLUS);
        }
    }

    private static boolean isCurrencySymbol(String text) {
        if (text.codePointCount(0, text.length()) != 1) {
            return false;
        }
        int codePoint = text.codePointAt(0);
        return Character.getType(codePoint) == Character.CURRENCY_SYMBOL || codePoint == YUAN;
    }

    private static boolean isWordPart(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || isNonLetterOrDigitWordPart(codePoint);
    }

    private static boolean isNumberPart(int codePoint) {
        return Character.isDigit(codePoint)
                || isNonLetterOrDigitWordPart(codePoint)
                || codePoint == PLUS
                || codePoint == NUMBER_SIGN
                || MystemUnicodeGroups.OTHER_PUNCTUATION_NUMBER_PARTS.contains(codePoint);
    }

    private static boolean isNonLetterOrDigitWordPart(int codePoint) {
        int type = Character.getType(codePoint);
        return MystemUnicodeGroups.OTHER_SYMBOL_ARABIC_START_OF_RUB_EL_HIZB.contains(codePoint)
                || MystemUnicodeGroups.OTHER_PUNCTUATION_ARMENIAN.contains(codePoint)
                || (type == Character.COMBINING_SPACING_MARK
                        && !MystemUnicodeGroups.COMBINING_SPACING_MARKS_NEW.contains(codePoint))
                || MystemUnicodeGroups.LETTER_NUMBERS_RUSSIAN_HOMOGLYPH_ROMAN.contains(codePoint)
                || type == Character.ENCLOSING_MARK
                || (type == Character.NON_SPACING_MARK
                        && !MystemUnicodeGroups.NON_SPACING_MARKS_NEW.contains(codePoint)
                        && !MystemUnicodeGroups.NON_SPACING_MARKS_KHMER.contains(codePoint))
                || (type == Character.LETTER_NUMBER && !MystemUnicodeGroups.LETTER_NUMBERS_NEW.contains(codePoint))
                || (type == Character.OTHER_NUMBER && !MystemUnicodeGroups.OTHER_NUMBERS_NEW.contains(codePoint))
                || codePoint == SOFT_HYPHEN;
    }

    private static boolean isSeparator(int codePoint) {
        if (codePoint == '"') {
            return true;
        }
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || type == Character.CONTROL
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.FORMAT;
    }

    private enum TokenFeature {
        WORD,
        RUSSIAN_LEMMATIZED_WORD,
        NUMBER,
        ENDS_WITH_NUMBER_SIGN,
        ENDS_WITH_PLUS,
        ENDS_WITH_DOUBLE_PLUSES,
        SEPARATOR,
        URL_PART,
        EMAIL_PART,
        URL,
        EMAIL,
        CURRENCY
    }

    @FunctionalInterface
    private interface CodePointPredicate {
        boolean test(int codePoint);
    }

    private record MergeRange(int firstIndex, int lastIndex, int startOffset, int endOffset) {}

    private static final class PreparedToken {
        private final String text;
        private final List<String> lemmas;
        private final ArrayList<String> forms;
        private final EnumSet<TokenFeature> features;
        private int startOffset;
        private int endOffset;

        private PreparedToken(
                String text,
                List<String> lemmas,
                List<String> forms,
                EnumSet<TokenFeature> features,
                int startOffset,
                int endOffset) {
            this.text = text;
            this.lemmas = List.copyOf(lemmas);
            this.forms = new ArrayList<>(forms);
            this.features = features.clone();
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        private static PreparedToken from(MystemToken token, MystemSearchTokenizerOptions options) {
            ArrayList<String> lemmas = new ArrayList<>();
            for (MystemAnalysis analysis : token.analyses()) {
                if (!analysis.lemma().isEmpty() && !lemmas.contains(analysis.lemma())) {
                    lemmas.add(analysis.lemma());
                }
            }
            EnumSet<TokenFeature> features = detectFeatures(token.text(), lemmas, options);
            return new PreparedToken(token.text(), lemmas, List.of(), features, token.startOffset(), token.endOffset());
        }

        private static PreparedToken gap(
                String text, int startOffset, int endOffset, MystemSearchTokenizerOptions options) {
            return new PreparedToken(
                    text, List.of(), List.of(), detectFeatures(text, List.of(), options), startOffset, endOffset);
        }

        private static PreparedToken composite(String text, int startOffset, int endOffset, TokenFeature feature) {
            return new PreparedToken(text, List.of(), List.of(), EnumSet.of(feature), startOffset, endOffset);
        }

        private PreparedToken withOffsets(int startOffset, int endOffset) {
            return new PreparedToken(text, lemmas, forms, features, startOffset, endOffset);
        }
    }
}
