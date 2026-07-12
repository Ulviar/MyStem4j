package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemOffsetAlignerContextTest {
    private static final char SOFT_HYPHEN = '\u00AD';
    private static final String TOKEN = "Один";

    @Test
    void alignsEveryInternalSoftHyphenCombinationBeforeAnExactDuplicate() {
        for (String variant : internalSoftHyphenVariants(TOKEN)) {
            assertRepeatedAlignment(variant, TOKEN);
        }
    }

    @Test
    void alignsEveryInternalSoftHyphenCombinationAfterAnExactDuplicate() {
        for (String variant : internalSoftHyphenVariants(TOKEN)) {
            assertRepeatedAlignment(TOKEN, variant);
        }
    }

    @Test
    void alignsTwoFuzzyDuplicatesFromLeftToRight() {
        List<String> variants = internalSoftHyphenVariants(TOKEN);
        for (String first : variants) {
            for (String second : variants) {
                assertRepeatedAlignment(first, second);
            }
        }
    }

    @Test
    void prefixingTextShiftsEveryAlignedRange() {
        String variant = "О" + SOFT_HYPHEN + "д" + SOFT_HYPHEN + "ин";
        String prefix = "метка: ";
        MystemOffsetAligner aligner = new MystemOffsetAligner(prefix + variant + " " + TOKEN);

        MystemTextRange prefixRange = aligner.align(prefix);
        MystemTextRange first = aligner.align(TOKEN);
        MystemTextRange second = aligner.align(TOKEN);

        assertEquals(new MystemTextRange(0, prefix.length()), prefixRange);
        assertEquals(new MystemTextRange(prefix.length(), prefix.length() + variant.length()), first);
        assertEquals(
                new MystemTextRange(
                        prefix.length() + variant.length() + 1,
                        prefix.length() + variant.length() + 1 + TOKEN.length()),
                second);
        assertTrue(aligner.issues().isEmpty());
    }

    private static void assertRepeatedAlignment(String firstSurface, String secondSurface) {
        String original = firstSurface + " " + secondSurface;
        MystemOffsetAligner aligner = new MystemOffsetAligner(original);

        MystemTextRange first = aligner.align(TOKEN);
        MystemTextRange second = aligner.align(TOKEN);

        assertEquals(
                new MystemTextRange(0, firstSurface.length()),
                first,
                () -> diagnostic("first", original, firstSurface, secondSurface, first));
        assertEquals(
                new MystemTextRange(firstSurface.length() + 1, original.length()),
                second,
                () -> diagnostic("second", original, firstSurface, secondSurface, second));
        assertTrue(
                aligner.issues().isEmpty(),
                () -> "Unexpected issues for " + printable(original) + ": " + aligner.issues());
    }

    private static List<String> internalSoftHyphenVariants(String token) {
        int boundaries = token.codePointCount(0, token.length()) - 1;
        ArrayList<String> variants = new ArrayList<>((1 << boundaries) - 1);
        for (int mask = 1; mask < (1 << boundaries); mask++) {
            StringBuilder variant = new StringBuilder(token.length() + boundaries);
            int codePointIndex = 0;
            for (int offset = 0; offset < token.length(); ) {
                int codePoint = token.codePointAt(offset);
                variant.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
                if (offset < token.length() && (mask & (1 << codePointIndex)) != 0) {
                    variant.append(SOFT_HYPHEN);
                }
                codePointIndex++;
            }
            variants.add(variant.toString());
        }
        return List.copyOf(variants);
    }

    private static String diagnostic(
            String occurrence,
            String original,
            String firstSurface,
            String secondSurface,
            MystemTextRange actual) {
        return occurrence + " occurrence aligned incorrectly: input=" + printable(original)
                + ", first=" + printable(firstSurface)
                + ", second=" + printable(secondSurface)
                + ", actual=" + actual;
    }

    private static String printable(String value) {
        return value.replace(String.valueOf(SOFT_HYPHEN), "<SHY>");
    }
}
