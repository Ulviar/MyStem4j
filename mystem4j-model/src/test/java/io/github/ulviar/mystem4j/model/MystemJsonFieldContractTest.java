package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MystemJsonFieldContractTest {
    private final MystemJsonParser parser = new MystemJsonParser();

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("wrongFieldTypes")
    void rejectsPresentKnownFieldsWithWrongTypes(String field, String value, String json) {
        MystemJsonParseException error =
                assertThrows(MystemJsonParseException.class, () -> parser.parse("мама", json));

        assertTrue(error.getMessage().contains("'" + field + "'"), error.getMessage());
        assertTrue(error.getMessage().contains("line"), error.getMessage());
        assertTrue(error.getMessage().contains("column"), error.getMessage());
    }

    static Stream<Arguments> wrongFieldTypes() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String field : List.of("text", "analysis", "lex", "gr", "wt")) {
            for (String value : List.of("null", "true", "{}", "[]", "12", "\"value\"")) {
                boolean valid = switch (field) {
                    case "analysis" -> value.equals("[]");
                    case "wt" -> value.equals("12");
                    default -> value.equals("\"value\"");
                };
                if (valid) {
                    continue;
                }
                String property = "\"" + field + "\":" + value;
                String json = switch (field) {
                    case "text", "analysis" -> "[{" + property + "}]";
                    default -> "[{\"text\":\"мама\",\"analysis\":[{" + property + "}]}]";
                };
                cases.add(Arguments.of(field, value, json));
            }
        }
        return cases.build();
    }

    @Test
    void reportsTheMalformedFieldInALaterTopLevelArray() {
        MystemJsonParseException error = assertThrows(
                MystemJsonParseException.class,
                () -> parser.parse("мама папа", """
                        [{"text":"мама"}]
                        [{"text":"папа","analysis":[{"lex":"папа","gr":false}]}]
                        """));

        assertTrue(error.getMessage().contains("'gr'"), error.getMessage());
        assertTrue(error.getMessage().contains("line 2, column"), error.getMessage());
    }

    @Test
    void retainsDefaultsForAbsentFieldsAndIgnoresUnknownNestedFields() {
        MystemDocument document = parser.parse("мама папа", """
                [{"unknown":{"text":null,"analysis":false}},
                 {"text":"мама","analysis":[{},
                   {"lex":"мама","gr":"S","wt":1,"extra":[{"wt":"ignored"}]}]}]
                [{"text":"папа","extra":null}]
                """);

        assertEquals(3, document.tokens().size());
        MystemToken empty = document.tokens().get(0);
        assertEquals("", empty.text());
        assertEquals(0, empty.startOffset());
        assertEquals(0, empty.endOffset());
        assertTrue(empty.analyses().isEmpty());
        MystemAnalysis defaultAnalysis = document.tokens().get(1).analyses().get(0);
        assertEquals("", defaultAnalysis.lemma());
        assertEquals("", defaultAnalysis.grammar().raw());
        assertFalse(defaultAnalysis.weight().isPresent());
        assertEquals(1.0, document.tokens().get(1).analyses().get(1).weight().orElseThrow());
        assertEquals("папа", document.tokens().get(2).text());
        assertEquals(5, document.tokens().get(2).startOffset());
        assertTrue(document.tokens().get(2).analyses().isEmpty());
        assertTrue(document.issues().isEmpty());
    }

    @Test
    void acceptsEmptyStringsEmptyAnalysesAndFractionalWeights() {
        MystemDocument document = parser.parse("мама", """
                [{"text":"","analysis":[]},
                 {"text":"мама","analysis":[{"lex":"","gr":"","wt":0.25}]}]
                """);

        assertTrue(document.tokens().get(0).analyses().isEmpty());
        MystemAnalysis analysis = document.tokens().get(1).analyses().get(0);
        assertEquals("", analysis.lemma());
        assertEquals("", analysis.grammar().raw());
        assertEquals(0.25, analysis.weight().orElseThrow());
    }
}
