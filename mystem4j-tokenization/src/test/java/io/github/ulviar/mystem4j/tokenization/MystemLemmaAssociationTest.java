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

class MystemLemmaAssociationTest {
    // MyStem 3.1 -cd --format=json. Only the synthetic final newline item is omitted.
    private static final String COPIED_SOFT_HYPHENS = """
            [{"analysis":[{"lex":"кот"}],"text":"Коты"},{"text":"\u00AD "},
             {"analysis":[{"lex":"собака"}],"text":"Собаки"},{"text":"\u00AD "},
             {"analysis":[{"lex":"мышь"}],"text":"Мыши"}]
            """;

    @Test
    void copiedSoftHyphenSeparatorsDoNotMoveAnalysesToAnotherWord() {
        for (String prefix : List.of("", "😀 ")) {
            String input = prefix + "Коты\u00AD Собаки\u00AD Мыши";
            MystemDocument document = new MystemJsonParser().parse(input, COPIED_SOFT_HYPHENS);
            for (MystemSearchTokenizerOptions options : presets()) {
                List<MystemSearchToken> tokens = new MystemSearchTokenizer(options).tokenize(document);
                List<MystemSearchToken> words = words(tokens);
                assertEquals(List.of("Коты\u00AD", "Собаки\u00AD", "Мыши"),
                        words.stream().map(MystemSearchToken::text).toList());
                assertLemmas(words, List.of(List.of("кот"), List.of("собака"), List.of("мышь")));
                assertEquals(List.of(prefix.length(), prefix.length() + 6, prefix.length() + 14),
                        words.stream().map(MystemSearchToken::startOffset).toList());
                assertEquals(List.of(prefix.length() + 5, prefix.length() + 13, prefix.length() + 18),
                        words.stream().map(MystemSearchToken::endOffset).toList());
                assertPartition(input, tokens);
            }
        }
    }

    @Test
    void clippedCopiedFragmentsAreClassifiedFromTheirRemainingSource() {
        String input = "Коты\u00AD,Собаки\u00AD,Мыши";
        MystemDocument document = new MystemJsonParser().parse(input, COPIED_SOFT_HYPHENS.replace("\u00AD ", "\u00AD,"));
        List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(document);
        assertLemmas(words(tokens), List.of(List.of("кот"), List.of("собака"), List.of("мышь")));
        List<MystemSearchToken> commas = tokens.stream().filter(token -> token.text().equals(",")).toList();
        assertEquals(2, commas.size());
        assertTrue(commas.stream().allMatch(token -> token.type() == MystemSearchTokenType.OTHER));
        assertPartition(input, tokens);
    }

    @Test
    void fullyConsumedCopiedSuffixDoesNotStealAnOccurrenceFromTheNextWord() {
        MystemDocument document = new MystemJsonParser().parse(
                "Один## Двое## Трое",
                """
                [{"analysis":[{"lex":"один#"}],"text":"Один"},{"text":"#"},{"text":" "},
                 {"analysis":[{"lex":"двое#"}],"text":"Двое"},{"text":"#"},{"text":" "},
                 {"analysis":[{"lex":"трое"}],"text":"Трое"}]
                """);
        List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(document);
        assertLemmas(words(tokens), List.of(List.of("один#", "один"), List.of("двое#", "двое"), List.of("трое")));
        assertPartition(document.originalText(), tokens);
    }

    @Test
    void rejectsOverlappingModelAnalysesInsteadOfAssigningThemToLaterOccurrences() {
        MystemDocument document = new MystemDocument(
                "замки замки",
                List.of(token("замки", 0, 5, "замок"), token("замки", 0, 5, "замыкать")),
                List.of());
        assertThrows(MystemTokenizationException.class, () -> new MystemSearchTokenizer().tokenize(document));
    }

    private static List<MystemSearchTokenizerOptions> presets() {
        return List.of(MystemSearchTokenizerOptions.conservative(), MystemSearchTokenizerOptions.search(),
                MystemSearchTokenizerOptions.entityAware());
    }

    private static List<MystemSearchToken> words(List<MystemSearchToken> tokens) {
        return tokens.stream().filter(token -> token.type() == MystemSearchTokenType.WORD).toList();
    }

    private static void assertLemmas(List<MystemSearchToken> words, List<List<String>> expected) {
        assertEquals(expected, words.stream().map(token -> token.forms().stream().map(MystemTokenForm::text).toList()).toList());
        assertTrue(words.stream().flatMap(token -> token.forms().stream()).allMatch(MystemTokenForm::keyword));
    }

    private static void assertPartition(String input, List<MystemSearchToken> tokens) {
        int cursor = 0;
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset());
            assertEquals(input.substring(token.startOffset(), token.endOffset()), token.text());
            assertTrue(token.endOffset() > cursor);
            cursor = token.endOffset();
        }
        assertEquals(input.length(), cursor);
    }

    private static MystemToken token(String text, int start, int end, String lemma) {
        return new MystemToken(text, start, end,
                List.of(new MystemAnalysis(lemma, MystemGrammarParser.parse(""), OptionalDouble.empty())));
    }
}
