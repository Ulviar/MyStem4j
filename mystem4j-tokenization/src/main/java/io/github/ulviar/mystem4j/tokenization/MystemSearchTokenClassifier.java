package io.github.ulviar.mystem4j.tokenization;

import java.util.EnumSet;
import java.util.List;

final class MystemSearchTokenClassifier {
    static final int SOFT_HYPHEN = 0x00AD;
    static final int PLUS = '+';
    static final int NUMBER_SIGN = '#';
    private static final int YUAN = '元';

    private MystemSearchTokenClassifier() {}

    static EnumSet<MystemTokenFeature> detectFeatures(
            String text, List<String> lemmas, MystemSearchTokenizerOptions options) {
        if (!lemmas.isEmpty()) {
            return EnumSet.of(MystemTokenFeature.WORD, MystemTokenFeature.RUSSIAN_LEMMATIZED_WORD);
        }
        if (options.classifyCurrencies() && isCurrencySymbol(text)) {
            return EnumSet.of(MystemTokenFeature.CURRENCY);
        }
        if (options.mergeEmails() && "@".equals(text)) {
            return EnumSet.of(MystemTokenFeature.EMAIL_PART);
        }
        if (options.mergeUrls() && "://".equals(text)) {
            return EnumSet.of(MystemTokenFeature.URL_PART);
        }
        EnumSet<MystemTokenFeature> result = EnumSet.noneOf(MystemTokenFeature.class);
        boolean allWordParts = true;
        boolean containsLetterOrDigit = false;
        boolean allNumberParts = true;
        boolean containsDigit = false;
        boolean containsSeparator = false;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            allWordParts = allWordParts && isWordPart(codePoint);
            containsLetterOrDigit = containsLetterOrDigit || Character.isLetterOrDigit(codePoint);
            allNumberParts = allNumberParts && isNumberPart(codePoint);
            containsDigit = containsDigit || Character.isDigit(codePoint);
            containsSeparator = containsSeparator || isSeparator(codePoint);
            index += Character.charCount(codePoint);
        }
        if (containsSeparator) {
            result.add(MystemTokenFeature.SEPARATOR);
        }
        if (allNumberParts && containsDigit) {
            result.add(MystemTokenFeature.NUMBER);
            result.add(MystemTokenFeature.WORD);
            addNumericSuffixFeatures(text, result);
        } else if (allWordParts && containsLetterOrDigit) {
            result.add(MystemTokenFeature.WORD);
        }
        return result;
    }

    static boolean isCurrencySymbol(String text) {
        if (text.codePointCount(0, text.length()) != 1) {
            return false;
        }
        int codePoint = text.codePointAt(0);
        return Character.getType(codePoint) == Character.CURRENCY_SYMBOL || codePoint == YUAN;
    }

    static boolean isWordPart(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || isNonLetterOrDigitWordPart(codePoint);
    }

    static boolean isNumberPart(int codePoint) {
        return Character.isDigit(codePoint)
                || isNonLetterOrDigitWordPart(codePoint)
                || codePoint == PLUS
                || codePoint == NUMBER_SIGN
                || MystemUnicodeGroups.OTHER_PUNCTUATION_NUMBER_PARTS.contains(codePoint);
    }

    static boolean isSeparator(int codePoint) {
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

    private static void addNumericSuffixFeatures(String text, EnumSet<MystemTokenFeature> features) {
        if (text.endsWith("#")) {
            features.add(MystemTokenFeature.ENDS_WITH_NUMBER_SIGN);
        } else if (text.endsWith("++")) {
            features.add(MystemTokenFeature.ENDS_WITH_DOUBLE_PLUSES);
        } else if (text.endsWith("+")) {
            features.add(MystemTokenFeature.ENDS_WITH_PLUS);
        }
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
}
