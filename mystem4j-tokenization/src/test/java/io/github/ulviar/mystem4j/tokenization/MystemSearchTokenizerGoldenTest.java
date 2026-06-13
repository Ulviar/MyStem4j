package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemGrammarParser;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class MystemSearchTokenizerGoldenTest {
    @Test
    void emitsFullEntityAwareSequenceWithOffsetsTypesAndForms() {
        String text = "A https://example.com, me@example.com!";
        MystemDocument document = document(
                text,
                tokenAt(text, "A"),
                tokenAt(text, " "),
                tokenAt(text, "https"),
                tokenAt(text, "://"),
                tokenAt(text, "example"),
                tokenAt(text, "."),
                tokenAt(text, "com", text.indexOf("example")),
                tokenAt(text, ","),
                tokenAt(text, " ", text.indexOf(",")),
                tokenAt(text, "me"),
                tokenAt(text, "@"),
                tokenAt(text, "example", text.indexOf("@")),
                tokenAt(text, ".", text.indexOf("@")),
                tokenAt(text, "com", text.indexOf("@")),
                tokenAt(text, "!"));

        List<MystemSearchToken> tokens =
                new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware()).tokenize(document);

        assertEquals(List.of(
                expected("A", 0, 1, MystemSearchTokenType.WORD, form("a", false)),
                expected(" ", 1, 2, MystemSearchTokenType.SEPARATOR, form(" ", true)),
                expected(
                        "https://example.com",
                        2,
                        21,
                        MystemSearchTokenType.URL,
                        form("https://example.com", true),
                        form("example.com", true)),
                expected(",", 21, 22, MystemSearchTokenType.OTHER, form(",", true)),
                expected(" ", 22, 23, MystemSearchTokenType.SEPARATOR, form(" ", true)),
                expected(
                        "me@example.com",
                        23,
                        37,
                        MystemSearchTokenType.EMAIL,
                        form("me@example.com", true),
                        form("example.com", true)),
                expected("!", 37, 38, MystemSearchTokenType.OTHER, form("!", true))),
                tokens);
    }

    @Test
    void relocatesRepeatedOverlappingMystemTokensToNextMatchingSlice() {
        MystemDocument document = document(
                "мама мама",
                token("мама", 0, 4, "мама"),
                token("мама", 0, 4, "мама"));

        List<MystemSearchToken> tokens =
                new MystemSearchTokenizer(MystemSearchTokenizerOptions.search()).tokenize(document);

        assertEquals(List.of(
                expected("мама", 0, 4, MystemSearchTokenType.WORD, form("мама", true)),
                expected(" ", 4, 5, MystemSearchTokenType.SEPARATOR, form(" ", true)),
                expected("мама", 5, 9, MystemSearchTokenType.WORD, form("мама", true))),
                tokens);
    }

    @Test
    void searchPresetClassifiesNumbersAndCurrenciesButDoesNotMergeEntities() {
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
                tokenAt(text, "example", text.indexOf("@")),
                tokenAt(text, ".", text.indexOf("@")),
                tokenAt(text, "com", text.indexOf("@")));

        List<MystemSearchToken> tokens =
                new MystemSearchTokenizer(MystemSearchTokenizerOptions.search()).tokenize(document);

        assertEquals(MystemSearchTokenType.NUMBER, token(tokens, "10#").type());
        assertEquals(MystemSearchTokenType.CURRENCY, token(tokens, "$").type());
        assertEquals(MystemSearchTokenType.WORD, token(tokens, "https").type());
        assertEquals(MystemSearchTokenType.OTHER, token(tokens, "://").type());
        assertEquals(MystemSearchTokenType.OTHER, token(tokens, "@").type());
        assertEquals(0, tokens.stream().filter(token -> token.type() == MystemSearchTokenType.URL).count());
        assertEquals(0, tokens.stream().filter(token -> token.type() == MystemSearchTokenType.EMAIL).count());
    }

    private static MystemSearchToken expected(
            String text, int startOffset, int endOffset, MystemSearchTokenType type, MystemTokenForm... forms) {
        return new MystemSearchToken(text, List.of(forms), startOffset, endOffset, type);
    }

    private static MystemTokenForm form(String text, boolean keyword) {
        return new MystemTokenForm(text, keyword);
    }

    private static MystemSearchToken token(List<MystemSearchToken> tokens, String text) {
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
        ArrayList<MystemAnalysis> analyses = new ArrayList<>();
        for (String lemma : lemmas) {
            analyses.add(new MystemAnalysis(lemma, MystemGrammarParser.parse(""), OptionalDouble.empty()));
        }
        return new MystemToken(text, startOffset, endOffset, analyses);
    }
}
