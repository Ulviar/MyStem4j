package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MystemGrammarParserTest {
    @Test
    void parsesCommonAndInflectionGrammemes() {
        MystemGrammar grammar = MystemGrammarParser.parse("S,жен,од=им,ед");

        assertEquals("S", grammar.partOfSpeech().orElseThrow());
        assertEquals(Set.of("жен", "од"), grammar.commonGrammemes());
        assertEquals(1, grammar.variants().size());
        assertEquals(Set.of("им", "ед"), grammar.variants().get(0).grammemes());
        assertTrue(grammar.allGrammemes().containsAll(Set.of("жен", "од", "им", "ед")));
    }

    @Test
    void parsesAlternativeInflections() {
        MystemGrammar grammar = MystemGrammarParser.parse("A=вин,ед,полн,муж,неод|им,ед,полн,муж");

        assertEquals("A", grammar.partOfSpeech().orElseThrow());
        assertEquals(2, grammar.variants().size());
        assertEquals(Set.of("вин", "ед", "полн", "муж", "неод"), grammar.variants().get(0).grammemes());
        assertEquals(Set.of("им", "ед", "полн", "муж"), grammar.variants().get(1).grammemes());
    }

    @Test
    void parsesEmptyRightSide() {
        MystemGrammar grammar = MystemGrammarParser.parse("PR=");

        assertEquals("PR", grammar.partOfSpeech().orElseThrow());
        assertEquals(1, grammar.variants().size());
        assertTrue(grammar.variants().get(0).grammemes().isEmpty());
    }
}
