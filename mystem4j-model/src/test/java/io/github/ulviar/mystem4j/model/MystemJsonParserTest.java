package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MystemJsonParserTest {
    private final MystemJsonParser parser = new MystemJsonParser();

    @Test
    void parsesTokensAnalysesWeightsAndOffsets() {
        MystemDocument document = parser.parse(
                "Мама, мама.",
                """
                [
                  {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед","wt":0.98}],"text":"Мама"},
                  {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"мама"}
                ]
                """);

        assertEquals(2, document.tokens().size());
        assertTrue(document.issues().isEmpty());
        MystemToken first = document.tokens().get(0);
        MystemToken second = document.tokens().get(1);
        assertEquals(0, first.startOffset());
        assertEquals(4, first.endOffset());
        assertEquals(6, second.startOffset());
        assertEquals(10, second.endOffset());
        assertEquals("мама", first.analyses().get(0).lemma());
        assertEquals("S", first.analyses().get(0).grammar().partOfSpeech().orElseThrow());
        assertEquals(0.98, first.analyses().get(0).weight().orElseThrow(), 0.0001);
        assertFalse(second.analyses().get(0).weight().isPresent());
    }

    @Test
    void reportsUnmatchedTokens() {
        MystemDocument document = parser.parse(
                "мама",
                """
                [{"analysis":[{"lex":"папа","gr":"S,муж,од=им,ед"}],"text":"папа"}]
                """);

        assertEquals(-1, document.tokens().get(0).startOffset());
        assertEquals(MystemTextIssueType.UNMATCHED_TOKEN, document.issues().get(0).type());
    }

    @Test
    void mapsPreparedTextOffsetsBackToOriginalText() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("ма\u0000ма");

        MystemDocument document = parser.parse(
                prepared,
                """
                [
                  {"analysis":[{"lex":"ма","gr":"S"}],"text":"ма"},
                  {"analysis":[{"lex":"ма","gr":"S"}],"text":"ма"}
                ]
                """);

        assertEquals("ма\u0000ма", document.originalText());
        assertEquals(0, document.tokens().get(0).startOffset());
        assertEquals(2, document.tokens().get(0).endOffset());
        assertEquals(3, document.tokens().get(1).startOffset());
        assertEquals(5, document.tokens().get(1).endOffset());
        assertEquals(MystemTextIssueType.CONTROL_CHARACTER, document.issues().get(0).type());
    }

    @Test
    void alignsSupplementaryCharactersUsingUtf16Offsets() {
        String token = "до😀после";

        MystemDocument document = parser.parse(
                "x" + token + "y",
                """
                [{"analysis":[{"lex":"lemma","gr":"S"}],"text":"до😀после"}]
                """);

        assertEquals(1, document.tokens().get(0).startOffset());
        assertEquals(1 + token.length(), document.tokens().get(0).endOffset());
        assertEquals(token, document.originalText().substring(
                document.tokens().get(0).startOffset(), document.tokens().get(0).endOffset()));
    }

    @Test
    void parsesMultipleTopLevelArraysFromMultilineMystemOutput() {
        MystemDocument document = parser.parse(
                "мама\nпапа",
                """
                [{"analysis":[{"lex":"мама","gr":"S"}],"text":"мама"}]
                [{"analysis":[{"lex":"папа","gr":"S"}],"text":"папа"}]
                """);

        assertEquals(2, document.tokens().size());
        assertEquals(0, document.tokens().get(0).startOffset());
        assertEquals(4, document.tokens().get(0).endOffset());
        assertEquals(5, document.tokens().get(1).startOffset());
        assertEquals(9, document.tokens().get(1).endOffset());
    }

    @Test
    void alignsTokensWhenMystemDropsSoftHyphens() {
        MystemDocument document = parser.parse(
                "О\u00ADд\u00ADи\u00ADн",
                """
                [{"analysis":[{"lex":"один","gr":"S"}],"text":"Один"}]
                """);

        assertTrue(document.issues().isEmpty());
        assertEquals(0, document.tokens().get(0).startOffset());
        assertEquals(7, document.tokens().get(0).endOffset());
    }

    @Test
    void rejectsNonArrayJson() {
        MystemJsonParseException error =
                assertThrows(MystemJsonParseException.class, () -> parser.parse("text", "{\"text\":\"text\"}"));

        assertTrue(error.getMessage().contains("MyStem JSON root must be an array"));
        assertTrue(error.getMessage().contains("line"));
        assertTrue(error.getMessage().contains("column"));
    }

    @Test
    void reportsJsonSyntaxErrorLocation() {
        MystemJsonParseException error =
                assertThrows(MystemJsonParseException.class, () -> parser.parse("text", "[{\"text\":\"text\"}"));

        assertTrue(error.getMessage().contains("Failed to parse MyStem JSON output"));
        assertTrue(error.getMessage().contains("line"));
        assertTrue(error.getMessage().contains("column"));
    }
}
