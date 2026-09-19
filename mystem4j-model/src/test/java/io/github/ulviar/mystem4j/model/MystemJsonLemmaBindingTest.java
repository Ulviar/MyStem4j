package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MystemJsonLemmaBindingTest {
    @Test
    void copiedSoftHyphenSeparatorsLeaveEachAnalysisAtItsOriginalWord() {
        // MyStem 3.1 -cd --format=json, excluding the synthetic final newline item.
        String original = "😀 Коты\u00AD Собаки\u00AD Мыши";
        MystemDocument document = new MystemJsonParser().parse(original, """
                [{"analysis":[{"lex":"кот"}],"text":"Коты"},{"text":"\u00AD "},
                 {"analysis":[{"lex":"собака"}],"text":"Собаки"},{"text":"\u00AD "},
                 {"analysis":[{"lex":"мышь"}],"text":"Мыши"}]
                """);

        assertTrue(document.issues().isEmpty());
        assertEquals(List.of(3, 7, 9, 15, 17), document.tokens().stream().map(MystemToken::startOffset).toList());
        assertEquals(List.of(7, 9, 15, 17, 21), document.tokens().stream().map(MystemToken::endOffset).toList());
        assertEquals(List.of("кот", "собака", "мышь"), document.tokens().stream()
                .flatMap(token -> token.analyses().stream()).map(MystemAnalysis::lemma).toList());
        assertEquals(List.of("Коты", "Собаки", "Мыши"), document.tokens().stream()
                .filter(token -> !token.analyses().isEmpty())
                .map(token -> original.substring(token.startOffset(), token.endOffset())).toList());
    }

    @Test
    void repeatedSurfacesWithDifferentAnalysesKeepTheirLeftToRightAssociations() {
        String original = "За\u00ADмки Замки За\u00ADмки";
        MystemDocument document = new MystemJsonParser().parse(original, """
                [{"text":"Замки","analysis":[{"lex":"замок"}]},
                 {"text":"Замки","analysis":[{"lex":"замыкать"}]},
                 {"text":"Замки","analysis":[{"lex":"замок"}]}]
                """);

        assertTrue(document.issues().isEmpty());
        assertEquals(List.of(0, 7, 13), document.tokens().stream().map(MystemToken::startOffset).toList());
        assertEquals(List.of(6, 12, 19), document.tokens().stream().map(MystemToken::endOffset).toList());
        assertEquals(List.of("замок", "замыкать", "замок"), document.tokens().stream()
                .map(token -> token.analyses().getFirst().lemma()).toList());
    }
}
