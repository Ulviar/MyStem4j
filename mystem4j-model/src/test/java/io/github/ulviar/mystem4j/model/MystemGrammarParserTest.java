package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
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
    void stripsVariantGroupParenthesesFromMergedGrammarOutput() {
        MystemGrammar grammar = MystemGrammarParser.parse("S,жен=(пр,ед|пр,мн)");

        assertEquals("S", grammar.partOfSpeech().orElseThrow());
        assertEquals(Set.of("жен"), grammar.commonGrammemes());
        assertEquals(2, grammar.variants().size());
        assertEquals(Set.of("пр", "ед"), grammar.variants().get(0).grammemes());
        assertEquals(Set.of("пр", "мн"), grammar.variants().get(1).grammemes());
    }

    @Test
    void parsesEmptyRightSide() {
        MystemGrammar grammar = MystemGrammarParser.parse("PR=");

        assertEquals("PR", grammar.partOfSpeech().orElseThrow());
        assertEquals(1, grammar.variants().size());
        assertTrue(grammar.variants().get(0).grammemes().isEmpty());
    }

    @Test
    void retainsEmptyAlternativesIncludingTrailingDelimiters() {
        assertVariants("S=|", Set.of(), Set.of());
        assertVariants("S=||", Set.of(), Set.of(), Set.of());
        assertVariants("=|", Set.of(), Set.of());
        assertVariants("S=им|", Set.of("им"), Set.of());
        assertVariants("S=|им||род|", Set.of(), Set.of("им"), Set.of(), Set.of("род"), Set.of());
        assertVariants("S=(|)", Set.of(), Set.of());
        assertVariants("S=(им|)|", Set.of("им"), Set.of(), Set.of());
    }

    @Test
    void stripsBracketGroupsUsingStringTrimWhitespaceSemantics() {
        assertVariants("S=\u0000 (\t ( им \r) \n) \u001F", Set.of("им"));
        assertVariants("S=((( )))", Set.of());
        assertVariants("S=)им(", Set.of(")им("));
        assertVariants("S=(\u2003им\u2003)", Set.of("\u2003им\u2003"));
        assertVariants("S=\u2003(им)\u2003", Set.of("\u2003(им)\u2003"));
    }

    @Test
    void stripsDeepBracketGroupsWithinBoundedTime() {
        String raw = "S=" + "( \t".repeat(200_000) + "им,ед" + "\r )".repeat(200_000);

        assertTimeout(Duration.ofSeconds(5), () -> {
            MystemGrammar grammar = MystemGrammarParser.parse(raw);
            assertEquals(raw, grammar.raw());
            assertEquals(List.of(new MystemGrammarVariant(Set.of("им", "ед"))), grammar.variants());
        });
    }

    @SafeVarargs
    private static void assertVariants(String raw, Set<String>... alternatives) {
        MystemGrammar grammar = MystemGrammarParser.parse(raw);
        assertEquals(raw, grammar.raw());
        assertEquals(alternatives.length, grammar.variants().size(), raw);
        for (int index = 0; index < alternatives.length; index++) {
            assertEquals(alternatives[index], grammar.variants().get(index).grammemes(), raw);
        }
    }
}
