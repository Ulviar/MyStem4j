package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
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
        assertEquals(0, mappingCount(prepared));
    }

    @Test
    void replacesLineSeparatorsForJsonLineProtocol() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepareJsonLine("a\r\nb\nc");

        assertEquals("a  b c", prepared.text());
        assertEquals(3, prepared.issues().size());
        assertEquals(MystemTextIssueType.CONTROL_CHARACTER, prepared.issues().get(0).type());
        assertEquals(2, prepared.originalOffsetFor(2));
        assertEquals(4, prepared.originalOffsetFor(4));
        assertEquals(6, prepared.originalOffsetFor(6));
    }

    @Test
    void replacesUnicodeNoncharactersWithSpaces() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("a\uDBFF\uDFFFb\uFDD0c");

        assertEquals("a b c", prepared.text());
        assertEquals(2, prepared.issues().size());
        assertEquals(MystemTextIssueType.NONCHARACTER, prepared.issues().get(0).type());
        assertEquals(1, prepared.issues().get(0).offset());
        assertEquals(2, prepared.issues().get(0).length());
        assertEquals(MystemTextIssueType.NONCHARACTER, prepared.issues().get(1).type());
        assertEquals(3, prepared.originalOffsetFor(2));
        assertEquals(5, prepared.originalOffsetFor(4));
        assertEquals(6, prepared.originalOffsetFor(5));
        assertTrue(mappingCount(prepared) <= 3);
    }

    @Test
    void doesNotMaterializeMappingsForIdentityOffsets() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("a".repeat(10_000));

        assertEquals(0, mappingCount(prepared));
        assertEquals(9_999, prepared.originalOffsetFor(9_999));
        assertEquals(10_000, prepared.originalOffsetFor(10_000));
    }

    private static int mappingCount(MystemPreparedText prepared) {
        try {
            Field field = MystemPreparedText.class.getDeclaredField("mappings");
            field.setAccessible(true);
            return ((List<?>) field.get(prepared)).size();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
