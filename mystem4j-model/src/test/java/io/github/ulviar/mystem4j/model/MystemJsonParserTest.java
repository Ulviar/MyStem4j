package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestStats;
import java.time.Duration;
import java.util.OptionalInt;
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
    void rejectsNonArrayJson() {
        assertThrows(MystemJsonParseException.class, () -> parser.parse("text", "{\"text\":\"text\"}"));
    }

    @Test
    void rejectsNonJsonRawResult() {
        MystemRawResult rawResult = new MystemRawResult(
                "text",
                "text",
                MystemOutputFormat.TEXT,
                new MystemRequestStats(
                        Duration.ZERO,
                        MystemExecutionMode.ONE_SHOT_TEXT,
                        4,
                        4,
                        4,
                        4,
                        OptionalInt.empty(),
                        false));

        assertThrows(MystemInvalidOptionsException.class, () -> parser.parse(rawResult));
    }
}
