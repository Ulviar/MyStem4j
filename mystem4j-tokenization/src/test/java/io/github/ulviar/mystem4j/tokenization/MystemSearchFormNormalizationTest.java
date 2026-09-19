package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MystemSearchFormNormalizationTest {
    private static final List<MystemSearchTokenizerOptions> PRESETS = List.of(
            MystemSearchTokenizerOptions.conservative(),
            MystemSearchTokenizerOptions.search(),
            MystemSearchTokenizerOptions.entityAware());

    @Test
    void removesSoftHyphensFromFallbackFormsWithoutChangingSourceRanges() {
        assertForms("Fo\u00ADur", "Four", List.of("four"), false, false);
        assertForms("11\u00AD22", "1122", List.of("1122"), true, true);
        assertForms("Four\u00AD", "Four", List.of("four"), false, false);
        assertForms("1122\u00AD", "1122", List.of("1122"), true, true);
    }

    @ParameterizedTest
    @MethodSource("normalizedSuffixCases")
    void composesSoftHyphenAndExceptionalDiacriticRemovalWithSuffixForms(
            String source, String mystemText, List<String> expectedForms, boolean number) {
        assertForms(source, mystemText, expectedForms, number, number);
    }

    private static Stream<Arguments> normalizedSuffixCases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String mark : List.of("\u00AD", "\u0301", "\u0341", "\u00AD\u0301\u0341")) {
            for (String suffix : List.of("+", "++", "#")) {
                String word = "Fo" + mark + "ur";
                String number = "11" + mark + "22";
                cases.add(Arguments.of(
                        word + suffix, word.replace("\u00AD", ""), List.of("four" + suffix, "four"), false));
                cases.add(Arguments.of(
                        number + suffix,
                        number.replace("\u00AD", "") + suffix,
                        List.of("1122" + suffix, "1122"),
                        true));
            }
        }
        return cases.build();
    }

    @Test
    void preservesMeaningfulCombiningMarksAndSupplementaryLetters() {
        assertForms("\uD835\uDD3Do\u0308\u0301o++", "\uD835\uDD3Do\u0308\u0301o",
                List.of("\uD835\uDD3Do\u0308o++", "\uD835\uDD3Do\u0308o"), false, false);
        assertForms("\u0915\u093f\u0301#", "\u0915\u093f\u0301",
                List.of("\u0915\u093f#", "\u0915\u093f"), false, false);
    }

    @Test
    void normalizesLemmaFormsAndKeepsTheirKeywordSemantics() {
        String source = "Дву\u0301х++";
        MystemDocument document = new MystemJsonParser().parse(source,
                "[{\"text\":\"Дву́х\",\"analysis\":[{\"lex\":\"Два́++\"}]}]");
        for (MystemSearchTokenizerOptions options : PRESETS) {
            List<MystemSearchToken> tokens = new MystemSearchTokenizer(options).tokenize(document);
            assertEquals(List.of(new MystemSearchToken(source,
                    List.of(new MystemTokenForm("два++", true), new MystemTokenForm("два", true)),
                    0, source.length(), MystemSearchTokenType.WORD)), tokens);
        }
    }

    @Test
    void normalizesSynthesizedFallbackWordsAndNumbers() {
        String source = "Fo\u00ADur 11\u00AD22";
        MystemDocument document = new MystemJsonParser().parse(source, "[]");
        for (MystemSearchTokenizerOptions options : PRESETS) {
            List<MystemSearchToken> tokens = new MystemSearchTokenizer(options).tokenize(document);
            assertPartition(source, tokens);
            assertEquals(List.of(new MystemTokenForm("four", false)), tokens.getFirst().forms());
            assertEquals(List.of(new MystemTokenForm("1122", true)), tokens.getLast().forms());
        }
    }

    @Test
    void preservesStandaloneMarksAsLiteralSourceTokens() {
        String source = "\u00AD\u0301\u0341";
        List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(
                new MystemJsonParser().parse(source, "[]"));
        assertPartition(source, tokens);
        assertEquals(source, tokens.stream().flatMap(token -> token.forms().stream())
                .map(MystemTokenForm::text).reduce("", String::concat));
    }

    @Test
    void fallsBackToSourceWhenAllSelectedLemmasNormalizeToEmpty() {
        for (String source : List.of("Текст", "\u00AD\u0301\u0341")) {
            MystemDocument document = new MystemJsonParser().parse(source,
                    "[{\"text\":\"" + source + "\",\"analysis\":[{\"lex\":\"́́\"}]}]");
            List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(document);
            assertEquals(List.of(new MystemSearchToken(source,
                    List.of(new MystemTokenForm(source.equals("Текст") ? "текст" : source, true)),
                    0, source.length(), MystemSearchTokenType.WORD)), tokens);
        }
    }

    private static void assertForms(
            String source, String mystemText, List<String> expectedForms, boolean keyword, boolean number) {
        // The prefix catches accidental code-point offsets where Java UTF-16 ranges are required.
        String original = "😀 " + source;
        MystemDocument document = new MystemJsonParser().parse(original,
                "[{\"text\":\"" + mystemText + "\"}]");
        for (MystemSearchTokenizerOptions options : PRESETS) {
            List<MystemSearchToken> tokens = new MystemSearchTokenizer(options).tokenize(document);
            assertPartition(original, tokens);
            MystemSearchToken token = tokens.getLast();
            assertEquals(source, token.text());
            assertEquals(3, token.startOffset());
            assertEquals(original.length(), token.endOffset());
            assertEquals(number && options.classifyNumbers() ? MystemSearchTokenType.NUMBER : MystemSearchTokenType.WORD,
                    token.type());
            assertEquals(expectedForms.stream().map(value -> new MystemTokenForm(value, keyword)).toList(),
                    token.forms(), source);
        }
    }

    private static void assertPartition(String source, List<MystemSearchToken> tokens) {
        int cursor = 0;
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset());
            assertEquals(source.substring(token.startOffset(), token.endOffset()), token.text());
            assertFalse(token.forms().isEmpty());
            cursor = token.endOffset();
        }
        assertEquals(source.length(), cursor);
    }
}
