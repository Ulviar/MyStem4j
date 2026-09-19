package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.ulviar.mystem4j.model.MystemDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemSearchTermNormalizerTest {
    @Test
    void keepsLiteralFormsBeforeUniqueCanonicalAliasesAtTheSameSourceRange() {
        for (String[] example : List.of(new String[]{"İstanbul", "i\u0307stanbul", "istanbul"},
                new String[]{"ΟΣ", "ος", "οσ"})) {
            List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(
                    new MystemDocument(example[0], List.of(), List.of()));
            assertEquals(1, tokens.size());
            MystemSearchToken token = tokens.getFirst();
            assertEquals(example[0], token.text());
            assertEquals(0, token.startOffset());
            assertEquals(example[0].length(), token.endOffset());
            assertEquals(List.of(new MystemTokenForm(example[1], false), new MystemTokenForm(example[2], false)), token.forms());
        }
        MystemSearchToken ordinary = new MystemSearchTokenizer().tokenize(
                new MystemDocument("FOUR", List.of(), List.of())).getFirst();
        assertEquals(List.of(new MystemTokenForm("four", false)), ordinary.forms());
    }

    @Test
    void normalizationIsIdempotentAndIndependentOfSurroundingCodePoints() {
        for (String part : List.of("Fo\u00AD", "ΟΣ", "İ", "O\u0308\u0301", "\uD801\uDC00", "*?\\", "\uD800")) {
            String normalized = MystemSearchTermNormalizer.normalize(part);
            assertEquals(normalized, MystemSearchTermNormalizer.normalize(normalized));
            assertEquals(normalized + "a", MystemSearchTermNormalizer.normalize(part + "A"));
        }
        assertEquals("ος", MystemSearchTermNormalizer.normalize("ος"));
        assertEquals("οσ", MystemSearchTermNormalizer.normalize("ΟΣ"));
        assertEquals("o\u0308", MystemSearchTermNormalizer.normalize("O\u0308\u0341"));
        assertEquals("\uD800", MystemSearchTermNormalizer.normalize("\uD800"));
        assertEquals("", MystemSearchTermNormalizer.normalize("\u00AD\u0301\u0341"));
        assertThrows(NullPointerException.class, () -> MystemSearchTermNormalizer.normalize(null));
    }

    @Test
    void entityLiteralDomainAndAliasesAreOrderedAndKeepKeywordOffsets() {
        String source = "https://example.com/Foo\u0301";
        MystemSearchToken token = new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware())
                .tokenize(new MystemDocument(source, List.of(), List.of())).getFirst();
        assertEquals(source, token.text());
        assertEquals(source.length(), token.endOffset());
        assertEquals(List.of(new MystemTokenForm("https://example.com/foo\u0301", true),
                new MystemTokenForm("example.com", true), new MystemTokenForm("https://example.com/foo", true)), token.forms());
        assertFalse(token.forms().stream().anyMatch(form -> form.text().isEmpty()));
    }
}
