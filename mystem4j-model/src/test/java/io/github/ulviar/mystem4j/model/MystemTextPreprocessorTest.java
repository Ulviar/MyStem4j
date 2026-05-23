package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MystemTextPreprocessorTest {
    @Test
    void preservesValidSupplementaryCodePoints() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("a😀b");

        assertEquals("a😀b", prepared.text());
        assertTrue(prepared.issues().isEmpty());
        assertEquals(1, prepared.originalOffsetFor(1));
        assertEquals(3, prepared.originalOffsetFor(3));
    }

    @Test
    void replacesUnpairedSurrogatesAndKeepsOffsetMapping() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("a\uD83Db");

        assertEquals("a\uFFFDb", prepared.text());
        assertEquals(1, prepared.issues().size());
        assertEquals(MystemTextIssueType.UNPAIRED_SURROGATE, prepared.issues().get(0).type());
        assertEquals(1, prepared.originalOffsetFor(1));
        assertEquals(2, prepared.originalOffsetFor(2));
    }

    @Test
    void replacesUnsafeControlCharactersWithSpaces() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("a\u0000b\nc");

        assertEquals("a b\nc", prepared.text());
        assertEquals(1, prepared.issues().size());
        assertEquals(MystemTextIssueType.CONTROL_CHARACTER, prepared.issues().get(0).type());
        assertEquals(1, prepared.issues().get(0).offset());
    }
}
