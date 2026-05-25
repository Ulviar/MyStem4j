package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemGrammarParser;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class MystemSearchTokenizerTest {
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void defaultTokenizerUsesConservativeSemanticPolicy() {
        String text = "10# $ https://example.com me@example.com";
        MystemDocument document = document(
                text,
                tokenAt(text, "10#"),
                tokenAt(text, " "),
                tokenAt(text, "$"),
                tokenAt(text, " ", text.indexOf("$")),
                tokenAt(text, "https"),
                tokenAt(text, "://"),
                tokenAt(text, "example"),
                tokenAt(text, "."),
                tokenAt(text, "com", text.indexOf("example")),
                tokenAt(text, " ", text.indexOf("com") + 3),
                tokenAt(text, "me"),
                tokenAt(text, "@"),
                tokenAt(text, "example", text.indexOf('@')),
                tokenAt(text, ".", text.indexOf('@')),
                tokenAt(text, "com", text.indexOf('@')));

        List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(document);

        assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "10#").type());
        assertEquals(List.of(new MystemTokenForm("10#", true), new MystemTokenForm("10", true)),
                tokenByText(tokens, "10#").forms());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "$").type());
        assertTrue(tokens.stream().noneMatch(token -> token.type() == MystemSearchTokenType.URL));
        assertTrue(tokens.stream().noneMatch(token -> token.type() == MystemSearchTokenType.EMAIL));
        assertTrue(tokens.stream().noneMatch(token -> token.type() == MystemSearchTokenType.CURRENCY));
        assertTrue(tokens.stream().noneMatch(token -> token.type() == MystemSearchTokenType.NUMBER));
    }

    @Test
    void rejectsCurrencyExpansionWithoutCurrencyClassification() {
        assertThrows(IllegalArgumentException.class,
                () -> new MystemSearchTokenizerOptions(false, false, false, false, true));
    }

    @Test
    void tokenizesSoftHyphenWordWithOriginalOffsets() {
        MystemDocument document = new MystemJsonParser().parse(
                "О\u00ADд\u00ADи\u00ADн",
                """
                [{"analysis":[{"lex":"один","gr":"S"}],"text":"Один"}]
                """);

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        assertEquals(1, tokens.size());
        assertEquals("О\u00ADд\u00ADи\u00ADн", tokens.get(0).text());
        assertEquals(0, tokens.get(0).startOffset());
        assertEquals(7, tokens.get(0).endOffset());
        assertEquals(MystemSearchTokenType.WORD, tokens.get(0).type());
        assertEquals(List.of(new MystemTokenForm("один", true)), tokens.get(0).forms());
    }

    @Test
    void restoresPlusAndNumberSignSuffixForms() {
        String text = "Раз++ два# C++ 10#";
        MystemDocument document = document(
                text,
                token("Раз", 0, 3, "раз++"),
                token(" ", 5, 6),
                token("два", 6, 9, "два#"),
                token(" ", 10, 11),
                token("C", 11, 12),
                token(" ", 14, 15),
                token("10#", 15, 18));

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        assertEquals(new MystemSearchToken(
                "Раз++",
                List.of(new MystemTokenForm("раз++", true), new MystemTokenForm("раз", true)),
                0,
                5,
                MystemSearchTokenType.WORD), tokens.get(0));
        assertEquals(new MystemSearchToken(
                "два#",
                List.of(new MystemTokenForm("два#", true), new MystemTokenForm("два", true)),
                6,
                10,
                MystemSearchTokenType.WORD), tokens.get(2));
        assertEquals(new MystemSearchToken(
                "C++",
                List.of(new MystemTokenForm("c++", false), new MystemTokenForm("c", false)),
                11,
                14,
                MystemSearchTokenType.WORD), tokens.get(4));
        assertEquals(new MystemSearchToken(
                "10#",
                List.of(new MystemTokenForm("10#", true), new MystemTokenForm("10", true)),
                15,
                18,
                MystemSearchTokenType.NUMBER), tokens.get(6));
    }

    @Test
    void mergesUrlAndEmailGroups() {
        String text = "Visit https://example.com or email me@example.com.";
        MystemDocument document = document(
                text,
                tokenAt(text, "Visit"),
                tokenAt(text, " "),
                tokenAt(text, "https"),
                tokenAt(text, "://"),
                tokenAt(text, "example"),
                tokenAt(text, "."),
                tokenAt(text, "com", text.indexOf('.')),
                tokenAt(text, " ", text.indexOf("com") + 3),
                tokenAt(text, "or"),
                tokenAt(text, " ", text.indexOf("or") + 2),
                tokenAt(text, "email"),
                tokenAt(text, " ", text.indexOf("email") + 5),
                tokenAt(text, "me"),
                tokenAt(text, "@"),
                tokenAt(text, "example", text.indexOf('@')),
                tokenAt(text, ".", text.indexOf('@')),
                tokenAt(text, "com", text.indexOf('@')),
                tokenAt(text, ".", text.indexOf('@') + 1),
                tokenAt(text, ".", text.lastIndexOf('.')));

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        MystemSearchToken url = tokens.stream()
                .filter(token -> token.type() == MystemSearchTokenType.URL)
                .findFirst()
                .orElseThrow();
        assertEquals("https://example.com", url.text());
        assertEquals(List.of(
                new MystemTokenForm("https://example.com", true),
                new MystemTokenForm("example.com", true)), url.forms());

        MystemSearchToken email = tokens.stream()
                .filter(token -> token.type() == MystemSearchTokenType.EMAIL)
                .findFirst()
                .orElseThrow();
        assertEquals("me@example.com", email.text());
        assertEquals(List.of(
                new MystemTokenForm("me@example.com", true),
                new MystemTokenForm("example.com", true)), email.forms());
        assertEquals(".", tokens.get(tokens.size() - 1).text());
        assertEquals(MystemSearchTokenType.OTHER, tokens.get(tokens.size() - 1).type());
    }

    @Test
    void synthesizesSearchTokensFromOriginalTextGaps() {
        String text = "О\u00ADд\u00ADи\u00ADн Раз++ C++ 10# $ https://example.com me@example.com.";
        MystemDocument document = new MystemJsonParser().parse(
                text,
                """
                [
                  {"analysis":[{"lex":"один","gr":"S"}],"text":"Один"},
                  {"analysis":[{"lex":"раз++","gr":"S"}],"text":"Раз"},
                  {"analysis":[],"text":"C"},
                  {"analysis":[],"text":"https"},
                  {"analysis":[],"text":"example"},
                  {"analysis":[],"text":"com"},
                  {"analysis":[],"text":"me"},
                  {"analysis":[],"text":"example"},
                  {"analysis":[],"text":"com"}
                ]
                """);

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "Раз++").type());
        assertEquals(List.of(new MystemTokenForm("раз++", true), new MystemTokenForm("раз", true)),
                tokenByText(tokens, "Раз++").forms());
        assertEquals(List.of(new MystemTokenForm("c++", false), new MystemTokenForm("c", false)),
                tokenByText(tokens, "C++").forms());
        assertEquals(MystemSearchTokenType.NUMBER, tokenByText(tokens, "10#").type());
        assertEquals(List.of(new MystemTokenForm("10#", true), new MystemTokenForm("10", true)),
                tokenByText(tokens, "10#").forms());
        assertEquals(MystemSearchTokenType.CURRENCY, tokenByText(tokens, "$").type());
        assertEquals(MystemSearchTokenType.URL, tokenByText(tokens, "https://example.com").type());
        assertEquals(MystemSearchTokenType.EMAIL, tokenByText(tokens, "me@example.com").type());
        assertEquals(".", tokens.get(tokens.size() - 1).text());
    }

    @Test
    void expandsCurrencySymbols() {
        MystemDocument document = document("$ € 元", token("$", 0, 1), token(" ", 1, 2), token("€", 2, 3),
                token(" ", 3, 4), token("元", 4, 5));

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        assertEquals(MystemSearchTokenType.CURRENCY, tokens.get(0).type());
        assertTrue(tokens.get(0).forms().contains(new MystemTokenForm("usd", true)));
        assertTrue(tokens.get(0).forms().contains(new MystemTokenForm("美元", true)));
        assertTrue(tokens.get(2).forms().contains(new MystemTokenForm("eur", true)));
        assertTrue(tokens.get(4).forms().contains(new MystemTokenForm("cny", true)));
    }

    @Test
    void removesExceptionalDiacriticsFromFallbackWordAndNumberForms() {
        MystemDocument document = document(
                "cafe\u0301 123\u0341",
                token("cafe\u0301", 0, 5),
                token(" ", 5, 6),
                token("123\u0341", 6, 10));

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        assertEquals(List.of(new MystemTokenForm("cafe", false)), tokens.get(0).forms());
        assertEquals(List.of(new MystemTokenForm("123", true)), tokens.get(2).forms());
    }

    @Test
    void preservesLegacyNumberClassificationBoundaries() {
        String text = "123Привет 2,20 10/11/1990 1-800-555-5555 50/50 $100 50% 1\u037E2 abc\u055E";
        MystemDocument document = document(
                text,
                tokenAt(text, "123Привет"),
                tokenAt(text, " "),
                tokenAt(text, "2,20"),
                tokenAt(text, " ", text.indexOf("2,20")),
                tokenAt(text, "10/11/1990"),
                tokenAt(text, " ", text.indexOf("10/11/1990")),
                tokenAt(text, "1-800-555-5555"),
                tokenAt(text, " ", text.indexOf("1-800-555-5555")),
                tokenAt(text, "50/50"),
                tokenAt(text, " ", text.indexOf("50/50")),
                tokenAt(text, "$100"),
                tokenAt(text, " ", text.indexOf("$100")),
                tokenAt(text, "50%"),
                tokenAt(text, " ", text.indexOf("50%")),
                tokenAt(text, "1\u037E2"),
                tokenAt(text, " ", text.indexOf("1\u037E2")),
                tokenAt(text, "abc\u055E"));

        List<MystemSearchToken> tokens = tokenizer.tokenize(document);

        assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "123Привет").type());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "2,20").type());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "10/11/1990").type());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "1-800-555-5555").type());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "50/50").type());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "$100").type());
        assertEquals(MystemSearchTokenType.OTHER, tokenByText(tokens, "50%").type());
        assertEquals(MystemSearchTokenType.NUMBER, tokenByText(tokens, "1\u037E2").type());
        assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "abc\u055E").type());
    }

    @Test
    void rejectsUnknownOffsets() {
        MystemDocument document = document("text", new MystemToken("text", -1, -1, List.of()));

        assertThrows(MystemTokenizationException.class, () -> tokenizer.tokenize(document));
    }

    @Test
    void rejectsOffsetsOutsideOriginalText() {
        MystemDocument document = document("x", new MystemToken("x", 0, 2, List.of()));

        assertThrows(MystemTokenizationException.class, () -> tokenizer.tokenize(document));
    }

    @Test
    void ignoresEmptyModelTokens() {
        MystemDocument document = document("x", token("", 0, 0), token("x", 0, 1));

        assertEquals(List.of(new MystemSearchToken(
                "x", List.of(new MystemTokenForm("x", false)), 0, 1, MystemSearchTokenType.WORD)),
                tokenizer.tokenize(document));
    }

    private static MystemSearchToken tokenByText(List<MystemSearchToken> tokens, String text) {
        return tokens.stream().filter(token -> token.text().equals(text)).findFirst().orElseThrow();
    }

    private static MystemDocument document(String text, MystemToken... tokens) {
        return new MystemDocument(text, List.of(tokens), List.of());
    }

    private static MystemToken tokenAt(String originalText, String tokenText) {
        return tokenAt(originalText, tokenText, 0);
    }

    private static MystemToken tokenAt(String originalText, String tokenText, int fromIndex) {
        int start = originalText.indexOf(tokenText, fromIndex);
        return token(tokenText, start, start + tokenText.length());
    }

    private static MystemToken token(String text, int startOffset, int endOffset, String... lemmas) {
        return new MystemToken(text, startOffset, endOffset, analyses(lemmas));
    }

    private static List<MystemAnalysis> analyses(String... lemmas) {
        return java.util.Arrays.stream(lemmas)
                .map(lemma -> new MystemAnalysis(lemma, MystemGrammarParser.parse(""), OptionalDouble.empty()))
                .toList();
    }
}
