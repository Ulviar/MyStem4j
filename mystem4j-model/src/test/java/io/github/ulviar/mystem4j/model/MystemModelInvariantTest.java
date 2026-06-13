package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MystemModelInvariantTest {
    @Test
    void preparedTextRequiresContiguousPreparedAndOriginalMappings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MystemPreparedText(
                        "abcd",
                        "abcd",
                        List.of(
                                new MystemOffsetMapping(0, 1, 0, 1),
                                new MystemOffsetMapping(2, 4, 1, 4)),
                        List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MystemPreparedText(
                        "abcd",
                        "abcd",
                        List.of(
                                new MystemOffsetMapping(0, 2, 0, 2),
                                new MystemOffsetMapping(2, 4, 3, 4)),
                        List.of()));
    }

    @Test
    void preparedTextMapsOffsetsWithValidatedBinarySearch() {
        MystemPreparedText prepared = new MystemPreparedText(
                "a\uDBFF\uDFFFb",
                "a b",
                List.of(
                        new MystemOffsetMapping(0, 1, 0, 1),
                        new MystemOffsetMapping(1, 2, 1, 3),
                        new MystemOffsetMapping(2, 3, 3, 4)),
                List.of());

        assertEquals(0, prepared.originalOffsetFor(0));
        assertEquals(1, prepared.originalOffsetFor(1));
        assertEquals(3, prepared.originalOffsetFor(2));
        assertEquals(4, prepared.originalOffsetFor(3));
    }

    @Test
    void tokenRequiresBothOffsetsKnownOrBothUnknown() {
        assertThrows(IllegalArgumentException.class, () -> new MystemToken("x", -1, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MystemToken("x", 0, -1, List.of()));
    }

    @Test
    void documentRejectsNullOriginalText() {
        assertThrows(NullPointerException.class, () -> new MystemDocument(null, List.of(), List.of()));
    }

    @Test
    void recordsDefensivelyCopyMutableCollections() {
        ArrayList<MystemAnalysis> analyses = new ArrayList<>();
        analyses.add(new MystemAnalysis("мама", MystemGrammarParser.parse("S"), java.util.OptionalDouble.empty()));
        MystemToken token = new MystemToken("Мама", 0, 4, analyses);

        analyses.clear();

        assertEquals(1, token.analyses().size());
        assertThrows(UnsupportedOperationException.class, () -> token.analyses().add(
                new MystemAnalysis("папа", MystemGrammarParser.parse("S"), java.util.OptionalDouble.empty())));

        ArrayList<MystemToken> tokens = new ArrayList<>();
        tokens.add(token);
        ArrayList<MystemTextIssue> issues = new ArrayList<>();
        issues.add(new MystemTextIssue(MystemTextIssueType.CONTROL_CHARACTER, "control", 1, 1));
        MystemDocument document = new MystemDocument("Мама", tokens, issues);

        tokens.clear();
        issues.clear();

        assertEquals(1, document.tokens().size());
        assertEquals(1, document.issues().size());
        assertThrows(UnsupportedOperationException.class, () -> document.tokens().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.issues().clear());
    }

    @Test
    void grammarCollectionsAreDefensiveAndImmutable() {
        HashSet<String> common = new HashSet<>(Set.of("жен", "од"));
        HashSet<String> variantGrammemes = new HashSet<>(Set.of("им", "ед"));
        MystemGrammarVariant variant = new MystemGrammarVariant(variantGrammemes);
        ArrayList<MystemGrammarVariant> variants = new ArrayList<>(List.of(variant));
        MystemGrammar grammar = new MystemGrammar("S,жен,од=им,ед", java.util.Optional.of("S"), common, variants);

        common.clear();
        variantGrammemes.clear();
        variants.clear();

        assertEquals(Set.of("жен", "од"), grammar.commonGrammemes());
        assertEquals(Set.of("им", "ед"), grammar.variants().getFirst().grammemes());
        assertTrue(grammar.allGrammemes().containsAll(Set.of("жен", "од", "им", "ед")));
        assertThrows(UnsupportedOperationException.class, () -> grammar.commonGrammemes().clear());
        assertThrows(UnsupportedOperationException.class, () -> grammar.variants().clear());
        assertThrows(UnsupportedOperationException.class, () -> variant.grammemes().clear());
    }
}
