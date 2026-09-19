package io.github.ulviar.mystem4j.tokenization;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts parsed MyStem tokens into search-oriented tokens.
 */
public final class MystemSearchTokenizer {
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
     * @throws MystemTokenizationException when a model token has invalid offsets or strict unmatched-token policy fails
     * @throws NullPointerException when {@code document} is {@code null}
     */
    public List<MystemSearchToken> tokenize(MystemDocument document) {
        Objects.requireNonNull(document, "document");
        List<MystemPreparedSearchToken> preparedTokens = prepareTokens(document, options);
        List<MystemPreparedSearchToken> mergedTokens =
                MystemCompositeTokenMerger.merge(document.originalText(), preparedTokens, options);
        ArrayList<MystemSearchToken> result = new ArrayList<>(mergedTokens.size());
        for (MystemPreparedSearchToken token : mergedTokens) {
            result.add(toSearchToken(document.originalText(), token, options));
        }
        return List.copyOf(result);
    }

    private static List<MystemPreparedSearchToken> prepareTokens(
            MystemDocument document, MystemSearchTokenizerOptions options) {
        ArrayList<MystemPreparedSearchToken> result = new ArrayList<>(document.tokens().size());
        String originalText = document.originalText();
        int cursor = 0;
        int previousModelEnd = 0;
        for (MystemToken token : document.tokens()) {
            if (!token.hasKnownOffsets()) {
                if (options.unmatchedTokenPolicy() == MystemUnmatchedTokenPolicy.FAIL) {
                    throw new MystemTokenizationException("Cannot tokenize MyStem token with unknown offsets: " + token);
                }
                continue;
            }
            validateTokenRange(originalText, token.text(), token.startOffset(), token.endOffset());
            if (token.text().isEmpty()) {
                continue;
            }
            if (token.startOffset() < previousModelEnd) {
                throw new MystemTokenizationException("MyStem token source ranges overlap or are out of order: "
                        + token.startOffset() + " < " + previousModelEnd);
            }
            previousModelEnd = token.endOffset();
            MystemPreparedSearchToken prepared = MystemPreparedSearchToken.from(token, options);
            if (prepared.startOffset < cursor) {
                prepared = trimConsumedPrefix(originalText, prepared, cursor, options);
                if (prepared == null) {
                    continue;
                }
                validateTokenRange(originalText, prepared.text, prepared.startOffset, prepared.endOffset);
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

    private static void validateTokenRange(String originalText, String text, int start, int end) {
        if (end > originalText.length()) {
            throw new MystemTokenizationException("MyStem token offsets exceed original text length: " + start + ".." + end);
        }
        if (!text.isEmpty() && start == end) {
            throw new MystemTokenizationException("Nonempty MyStem token has an empty source range: " + start);
        }
        if (splitsSurrogatePair(originalText, start) || splitsSurrogatePair(originalText, end)) {
            throw new MystemTokenizationException("MyStem token offsets split a UTF-16 surrogate pair: " + start + ".." + end);
        }
    }

    private static boolean splitsSurrogatePair(String text, int offset) {
        return offset > 0 && offset < text.length()
                && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset));
    }

    private static void appendGapTokens(
            String originalText,
            int startOffset,
            int endOffset,
            List<MystemPreparedSearchToken> result,
            MystemSearchTokenizerOptions options) {
        int index = startOffset;
        while (index < endOffset) {
            int nextOffset = nextGapTokenEnd(originalText, index, endOffset, options);
            String text = originalText.substring(index, nextOffset);
            result.add(MystemPreparedSearchToken.gap(text, index, nextOffset, options));
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
                || (options.classifyCurrencies()
                        && MystemSearchTokenClassifier.isCurrencySymbol(Character.toString(codePoint)))) {
            return startOffset + Character.charCount(codePoint);
        }
        if (MystemSearchTokenClassifier.isSeparator(codePoint)) {
            return runEnd(originalText, startOffset, limit, MystemSearchTokenClassifier::isSeparator);
        }
        if (MystemSearchTokenClassifier.isNumberPart(codePoint)) {
            return runEnd(originalText, startOffset, limit, MystemSearchTokenClassifier::isNumberPart);
        }
        if (MystemSearchTokenClassifier.isWordPart(codePoint)) {
            return runEnd(originalText, startOffset, limit, MystemSearchTokenClassifier::isWordPart);
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

    private static MystemPreparedSearchToken trimConsumedPrefix(
            String originalText, MystemPreparedSearchToken token, int cursor, MystemSearchTokenizerOptions options) {
        // Suffix recovery can consume the beginning of a following copied token.
        // Keep its remaining source range; looking for the text again would jump
        // over intervening words and detach their analyses from the original input.
        if (token.endOffset <= cursor) {
            return null;
        }
        return token.lemmas.isEmpty()
                ? MystemPreparedSearchToken.gap(
                        originalText.substring(cursor, token.endOffset), cursor, token.endOffset, options)
                : token.withOffsets(cursor, token.endOffset);
    }

    private static void extendTokenForDroppedSuffixes(String originalText, MystemPreparedSearchToken token) {
        if (!token.features.contains(MystemTokenFeature.WORD)) {
            return;
        }
        while (token.endOffset < originalText.length()
                && originalText.codePointAt(token.endOffset) == MystemSearchTokenClassifier.SOFT_HYPHEN) {
            token.endOffset += Character.charCount(MystemSearchTokenClassifier.SOFT_HYPHEN);
        }
        if (token.features.contains(MystemTokenFeature.ENDS_WITH_PLUS)
                || token.features.contains(MystemTokenFeature.ENDS_WITH_DOUBLE_PLUSES)
                || token.features.contains(MystemTokenFeature.ENDS_WITH_NUMBER_SIGN)) {
            return;
        }
        if (token.endOffset >= originalText.length()) {
            return;
        }
        int codePoint = originalText.codePointAt(token.endOffset);
        if (codePoint == MystemSearchTokenClassifier.NUMBER_SIGN) {
            token.features.add(MystemTokenFeature.ENDS_WITH_NUMBER_SIGN);
            token.endOffset += Character.charCount(MystemSearchTokenClassifier.NUMBER_SIGN);
        } else if (codePoint == MystemSearchTokenClassifier.PLUS) {
            token.endOffset += Character.charCount(MystemSearchTokenClassifier.PLUS);
            if (token.endOffset < originalText.length()
                    && originalText.codePointAt(token.endOffset) == MystemSearchTokenClassifier.PLUS) {
                token.features.add(MystemTokenFeature.ENDS_WITH_DOUBLE_PLUSES);
                token.endOffset += Character.charCount(MystemSearchTokenClassifier.PLUS);
            } else {
                token.features.add(MystemTokenFeature.ENDS_WITH_PLUS);
            }
        }
    }

    private static MystemSearchToken toSearchToken(
            String originalText, MystemPreparedSearchToken token, MystemSearchTokenizerOptions options) {
        String sourceText = originalText.substring(token.startOffset, token.endOffset);
        MystemSearchTokenType type = tokenType(token, options);
        return new MystemSearchToken(
                sourceText,
                MystemSearchTokenForms.forms(sourceText, token, type, options),
                token.startOffset,
                token.endOffset,
                type);
    }

    private static MystemSearchTokenType tokenType(
            MystemPreparedSearchToken token, MystemSearchTokenizerOptions options) {
        if (token.features.contains(MystemTokenFeature.URL)) {
            return MystemSearchTokenType.URL;
        }
        if (token.features.contains(MystemTokenFeature.EMAIL)) {
            return MystemSearchTokenType.EMAIL;
        }
        if (token.features.contains(MystemTokenFeature.CURRENCY)) {
            return MystemSearchTokenType.CURRENCY;
        }
        if (options.classifyNumbers() && token.features.contains(MystemTokenFeature.NUMBER)) {
            return MystemSearchTokenType.NUMBER;
        }
        if (token.features.contains(MystemTokenFeature.WORD)) {
            return MystemSearchTokenType.WORD;
        }
        if (token.features.contains(MystemTokenFeature.SEPARATOR)) {
            return MystemSearchTokenType.SEPARATOR;
        }
        return MystemSearchTokenType.OTHER;
    }

    @FunctionalInterface
    private interface CodePointPredicate {
        boolean test(int codePoint);
    }
}
