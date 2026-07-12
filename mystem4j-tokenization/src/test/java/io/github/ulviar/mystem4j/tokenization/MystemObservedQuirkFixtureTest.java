package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemObservedQuirkFixtureTest {
    private final MystemJsonParser parser = new MystemJsonParser();
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void replaysObservedPlusAndNumberSignSegmentationWithSafeOffsetsAndForms() {
        for (Fixture fixture : fixtures()) {
            MystemDocument document = parser.parse(fixture.input(), fixture.json());
            List<MystemSearchToken> tokens = tokenizer.tokenize(document);
            List<String> searchBearingText = tokens.stream()
                    .filter(MystemObservedQuirkFixtureTest::isSearchBearing)
                    .map(MystemSearchToken::text)
                    .toList();

            assertTrue(document.issues().isEmpty(), fixture.name() + ": " + document.issues());
            assertEquals(fixture.expectedSearchBearingText(), searchBearingText, fixture.name());
            assertPartition(fixture.input(), tokens, fixture.name());
            assertSuffixForms(tokens, fixture.name());
        }
    }

    private static List<Fixture> fixtures() {
        return List.of(
                new Fixture(
                        "single plus",
                        "Один+ two+ 3+ 4+",
                        """
                        [{"analysis":[{"lex":"один+"}],"text":"Один"},{"text":" "},
                         {"analysis":[],"text":"two"},{"text":" "},{"text":"3+"},{"text":" "},{"text":"4+"}]
                        """,
                        List.of("Один+", "two+", "3+", "4+")),
                new Fixture(
                        "double plus",
                        "Один++ two++ 3++ 4++",
                        """
                        [{"analysis":[{"lex":"один++"}],"text":"Один"},{"text":" "},
                         {"analysis":[],"text":"two"},{"text":" "},{"text":"3++"},{"text":" "},{"text":"4++"}]
                        """,
                        List.of("Один++", "two++", "3++", "4++")),
                new Fixture(
                        "triple plus",
                        "Один+++ two+++ 3+++ 4+++",
                        """
                        [{"analysis":[{"lex":"один++"}],"text":"Один"},{"text":"+ "},
                         {"analysis":[],"text":"two"},{"text":"+ "},{"text":"3++"},{"text":"+ "},
                         {"text":"4++"},{"text":"+"}]
                        """,
                        List.of("Один++", "two++", "3++", "4++")),
                new Fixture(
                        "single number sign",
                        "Один# two# 3# 4#",
                        """
                        [{"analysis":[{"lex":"один#"}],"text":"Один"},{"text":" "},
                         {"analysis":[],"text":"two"},{"text":" "},{"text":"3#"},{"text":" "},{"text":"4#"}]
                        """,
                        List.of("Один#", "two#", "3#", "4#")),
                new Fixture(
                        "double number sign",
                        "Один## two## 3## 4##",
                        """
                        [{"analysis":[{"lex":"один#"}],"text":"Один"},{"text":"#"},{"text":" "},
                         {"analysis":[],"text":"two"},{"text":"#"},{"text":" "},{"text":"3#"},
                         {"text":"#"},{"text":" "},{"text":"4#"},{"text":"#"}]
                        """,
                        List.of("Один#", "two#", "3#", "4#")));
    }

    private static void assertSuffixForms(List<MystemSearchToken> tokens, String label) {
        for (MystemSearchToken token : tokens) {
            if (!isSearchBearing(token) || !(token.text().endsWith("+") || token.text().endsWith("#"))) {
                continue;
            }
            String lowercase = token.text().toLowerCase(java.util.Locale.ROOT);
            assertTrue(
                    token.forms().stream().anyMatch(form -> form.text().equals(lowercase)),
                    () -> label + " is missing the suffixed form for " + token);
            int suffixLength = lowercase.endsWith("++") ? 2 : 1;
            String suffixless = lowercase.substring(0, lowercase.length() - suffixLength);
            assertTrue(
                    token.forms().stream().anyMatch(form -> form.text().equals(suffixless)),
                    () -> label + " is missing the suffixless form for " + token);
        }
    }

    private static void assertPartition(String input, List<MystemSearchToken> tokens, String label) {
        int cursor = 0;
        StringBuilder reconstructed = new StringBuilder(input.length());
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset(), () -> label + " has a gap or overlap: " + tokens);
            assertTrue(token.endOffset() > token.startOffset(), () -> label + " has an empty token: " + token);
            assertEquals(input.substring(token.startOffset(), token.endOffset()), token.text(), label);
            assertFalse(token.forms().isEmpty(), () -> label + " has no forms: " + token);
            reconstructed.append(token.text());
            cursor = token.endOffset();
        }
        assertEquals(input.length(), cursor, () -> label + " has an uncovered suffix: " + tokens);
        assertEquals(input, reconstructed.toString(), label);
    }

    private static boolean isSearchBearing(MystemSearchToken token) {
        return token.type() != MystemSearchTokenType.SEPARATOR && token.type() != MystemSearchTokenType.OTHER;
    }

    private record Fixture(String name, String input, String json, List<String> expectedSearchBearingText) {}
}
