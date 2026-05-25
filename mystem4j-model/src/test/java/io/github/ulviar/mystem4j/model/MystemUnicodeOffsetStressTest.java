package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class MystemUnicodeOffsetStressTest {
    private static final int UNICODE_SCALAR_VALUE_COUNT = 1_112_064;

    @Test
    void alignerUsesUtf16OffsetsForEveryUnicodeScalarValue() {
        int checked = 0;
        String prefix = "\u0001";
        String suffix = "\u0002";

        for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                continue;
            }
            String token = new String(Character.toChars(codePoint));
            MystemOffsetAligner aligner = new MystemOffsetAligner(prefix + token + suffix);

            aligner.align(prefix);
            MystemTextRange range = aligner.align(token);

            if (range.startOffset() != prefix.length() || range.endOffset() != prefix.length() + token.length()) {
                fail("Unexpected UTF-16 offsets for U+" + Integer.toHexString(codePoint).toUpperCase()
                        + ": " + range);
            }
            assertTrue(aligner.issues().isEmpty());
            checked++;
        }

        assertEquals(UNICODE_SCALAR_VALUE_COUNT, checked);
    }

    @Test
    void preprocessorMappingIsTotalAndMonotonicForEveryUtf16CodeUnit() {
        char[] codeUnits = new char[Character.MAX_VALUE + 1];
        for (int index = 0; index < codeUnits.length; index++) {
            codeUnits[index] = (char) index;
        }
        String original = new String(codeUnits);

        MystemPreparedText prepared = MystemTextPreprocessor.prepare(original);

        assertEquals(original.length(), prepared.text().length());
        assertEquals(0, prepared.originalOffsetFor(0));
        assertEquals(original.length(), prepared.originalOffsetFor(prepared.text().length()));
        assertFalse(prepared.issues().isEmpty());

        int previous = -1;
        for (int preparedOffset = 0; preparedOffset <= prepared.text().length(); preparedOffset++) {
            int originalOffset = prepared.originalOffsetFor(preparedOffset);
            assertTrue(originalOffset >= 0 && originalOffset <= original.length());
            assertTrue(originalOffset >= previous);
            previous = originalOffset;
        }
    }
}
