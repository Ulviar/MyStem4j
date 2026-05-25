package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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
}
