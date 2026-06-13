package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.annotation.WithUtf8Length;
import org.junit.jupiter.params.provider.ValueSource;

class MystemParserJazzerFuzzTest {
    private static final String ORIGINAL_TEXT = "мама папа email@example.com";

    private final MystemJsonParser parser = new MystemJsonParser();

    @FuzzTest
    @ValueSource(strings = {
        "[]",
        "[{\"text\":\"мама\",\"analysis\":[{\"lex\":\"мама\",\"gr\":\"S,жен=им,ед\",\"wt\":0.9}]}]",
        "[{\"text\":\"email@example.com\",\"analysis\":[]}]",
        "[{\"text\":123,\"analysis\":[{\"lex\":false,\"gr\":[],\"wt\":\"bad\"}]}]",
        "[{\"analysis\":[[]]}]",
        "{\"text\":\"мама\"}",
        "[{\"text\":\"мама\"}"
    })
    void fuzzJsonParser(@NotNull @WithUtf8Length(max = 2048) String json) {
        try {
            assertDocumentInvariants(parser.parse(ORIGINAL_TEXT, json));
        } catch (MystemJsonParseException expected) {
            assertFalse(expected.getMessage().isBlank());
        } catch (RuntimeException unexpected) {
            fail("Unexpected parser failure for input: " + json, unexpected);
        }
    }

    @FuzzTest
    @ValueSource(strings = {
        "",
        "S,жен,од=им,ед",
        "S,жен=(пр,ед|пр,мн)",
        "A=вин,ед,полн,муж,неод|им,ед,полн,муж",
        "PR=",
        "(|=,,)"
    })
    void fuzzGrammarParser(@NotNull @WithUtf8Length(max = 1024) String grammarString) {
        MystemGrammar grammar = MystemGrammarParser.parse(grammarString);

        assertEquals(grammarString, grammar.raw());
        grammar.partOfSpeech().ifPresent(partOfSpeech -> assertFalse(partOfSpeech.isBlank()));
        grammar.commonGrammemes().forEach(grammeme -> assertFalse(grammeme.isBlank()));
        assertFalse(grammar.variants().isEmpty());
        grammar.variants()
                .forEach(variant -> variant.grammemes().forEach(grammeme -> assertFalse(grammeme.isBlank())));
        grammar.allGrammemes().forEach(grammeme -> assertFalse(grammeme.isBlank()));
    }

    private static void assertDocumentInvariants(MystemDocument document) {
        assertEquals(ORIGINAL_TEXT, document.originalText());
        int previousKnownEnd = 0;
        for (MystemToken token : document.tokens()) {
            if (token.hasKnownOffsets()) {
                assertTrue(token.startOffset() <= ORIGINAL_TEXT.length());
                assertTrue(token.endOffset() <= ORIGINAL_TEXT.length());
                assertTrue(token.startOffset() >= previousKnownEnd);
                assertEquals(token.text(), ORIGINAL_TEXT.substring(token.startOffset(), token.endOffset()));
                previousKnownEnd = token.endOffset();
            } else {
                assertEquals(-1, token.startOffset());
                assertEquals(-1, token.endOffset());
            }
            token.analyses().forEach(analysis -> {
                assertEquals(analysis.grammar().raw(), MystemGrammarParser.parse(analysis.grammar().raw()).raw());
                assertFalse(analysis.grammar().variants().isEmpty());
            });
        }
        document.issues().forEach(issue -> {
            assertFalse(issue.message().isBlank());
            assertTrue(issue.offset() >= -1);
            assertTrue(issue.length() >= 0);
        });
    }
}
