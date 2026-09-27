package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

    @Test
    void includesLeadingSoftHyphensButLeavesTrailingOnesForTheNextToken() {
        MystemOffsetAligner aligner = new MystemOffsetAligner("!\u00AD\u00ADО\u00ADдин\u00AD\u00AD😀");

        assertEquals(new MystemTextRange(1, 8), aligner.align(TOKEN));
        assertEquals(new MystemTextRange(8, 8), aligner.align(""));
        assertEquals(new MystemTextRange(8, 12), aligner.align("😀"));
        assertTrue(aligner.issues().isEmpty());
    }

    @Test
    void recoversOverlappingPrefixesWithSupplementaryCharactersAndSoftHyphens() {
        String source = "😀\u00AD😀😀\u00AD😀b 😀😀b";
        MystemOffsetAligner aligner = new MystemOffsetAligner(source);

        assertEquals(new MystemTextRange(5, 11), aligner.align("😀😀b"));
        assertEquals(new MystemTextRange(12, source.length()), aligner.align("😀😀b"));
        assertTrue(aligner.issues().isEmpty());
    }

    @Test
    void copiedSoftHyphensRequireAnExactSurface() {
        MystemOffsetAligner aligner = new MystemOffsetAligner("\u00ADab a\u00ADb ab");

        assertEquals(new MystemTextRange(4, 7), aligner.align("a\u00ADb"));
        assertEquals(new MystemTextRange(8, 10), aligner.align("ab"));
        assertTrue(aligner.issues().isEmpty());
    }

    @Test
    void fuzzyMatchingDoesNotCombineSurrogatesAcrossDroppedCharacters() {
        MystemOffsetAligner aligner = new MystemOffsetAligner("\uD83D\u00AD\uDE00 😀");

        assertEquals(new MystemTextRange(4, 6), aligner.align("😀"));
        assertTrue(aligner.issues().isEmpty());
    }

    @Test
    void preservesExactUtf16MatchesForIsolatedSurrogateSurfaces() {
        MystemOffsetAligner aligner = new MystemOffsetAligner("😀x\u00AD\uDE00");

        assertEquals(new MystemTextRange(1, 2), aligner.align("\uDE00"));
        assertEquals(new MystemTextRange(3, 5), aligner.align("\uDE00"));
        assertTrue(aligner.issues().isEmpty());
    }

    @Test
    void unmatchedAndEmptySurfacesKeepTheAlignmentCursor() {
        MystemOffsetAligner aligner = new MystemOffsetAligner("ab\u00ADab");

        assertEquals(new MystemTextRange(0, 2), aligner.align("ab"));
        assertEquals(MystemTextRange.unknown(), aligner.align("ac"));
        assertEquals(2, aligner.issues().getFirst().offset());
        assertEquals(new MystemTextRange(2, 2), aligner.align(""));
        assertEquals(new MystemTextRange(2, 5), aligner.align("ab"));
    }

    @Test
    void composesFuzzyEndpointsAcrossPreparedSupplementaryContractions() {
        MystemPreparedText prepared = MystemTextPreprocessor.prepare("\uDBFF\uDFFF\u00AD😀\u00ADab 😀ab");
        MystemOffsetAligner aligner = new MystemOffsetAligner(prepared.text(), prepared::originalOffsetFor);

        assertEquals(new MystemTextRange(2, 8), aligner.align("😀ab"));
        assertEquals(new MystemTextRange(9, 13), aligner.align("😀ab"));
        assertTrue(aligner.issues().isEmpty());
    }

    @Test
    void searchesRepetitivePrefixesWithinBoundedTimeWithAndWithoutSoftHyphens() {
        String token = "a".repeat(200_000) + "b";
        assertTimeout(Duration.ofSeconds(5), () -> {
            for (String prefix : List.of("a".repeat(200_000), "a\u00AD".repeat(200_000))) {
                MystemOffsetAligner exact = new MystemOffsetAligner(prefix + " " + token);
                assertEquals(new MystemTextRange(prefix.length() + 1, prefix.length() + 1 + token.length()), exact.align(token));
                assertTrue(exact.issues().isEmpty());

                String fuzzyToken = "a\u00AD".repeat(200_000) + "b";
                MystemOffsetAligner fuzzy = new MystemOffsetAligner(prefix + " " + fuzzyToken + " " + token);
                assertEquals(new MystemTextRange(prefix.length() + 1, prefix.length() + 1 + fuzzyToken.length()), fuzzy.align(token));
                assertTrue(fuzzy.issues().isEmpty());

                MystemOffsetAligner unmatched = new MystemOffsetAligner(prefix);
                assertEquals(MystemTextRange.unknown(), unmatched.align(token));
                assertEquals(1, unmatched.issues().size());
            }
        });
    }

    @Test
    void generatedTokenSequencesMatchAnIndependentLeftToRightSearch() {
        Random random = new Random(0x5348594B4D50L);
        for (int attempt = 0; attempt < 2_000; attempt++) {
            String source = randomSurface(random, 30);
            MystemOffsetAligner aligner = new MystemOffsetAligner(source);
            int expectedCursor = 0;
            int expectedIssues = 0;
            for (int step = 0; step < 8; step++) {
                int start = random.nextInt(source.length() + 1);
                String token = random.nextBoolean()
                        ? source.substring(start, start + random.nextInt(source.length() - start + 1))
                                .replace(String.valueOf(SOFT_HYPHEN), "")
                        : randomSurface(random, 5);
                MystemTextRange expected = referenceRange(source, token, expectedCursor);

                assertEquals(expected, aligner.align(token), "attempt " + attempt + ", step " + step);
                if (expected.startOffset() < 0) {
                    expectedIssues++;
                    assertEquals(expectedCursor, aligner.issues().getLast().offset());
                    assertEquals(token.length(), aligner.issues().getLast().length());
                } else {
                    expectedCursor = expected.endOffset();
                }
                assertEquals(expectedIssues, aligner.issues().size());
            }
        }
    }

    @Test
    void alignsManyShortSparseTokensWithinBoundedTime() {
        int count = 500_000;
        String source = "x ab".repeat(count);
        assertTimeout(Duration.ofSeconds(5), () -> {
            for (String text : List.of(source, source + SOFT_HYPHEN)) {
                MystemOffsetAligner aligner = new MystemOffsetAligner(text);
                for (int index = 0; index < count; index++) {
                    assertEquals(new MystemTextRange(index * 4 + 2, index * 4 + 4), aligner.align("ab"));
                }
                assertTrue(aligner.issues().isEmpty());
            }
        });
    }

    @Test
    void alignsManySuccessfulFuzzyTokensWithoutRescanningTheRemainingSource() {
        int count = 100_000;
        String source = "О\u00ADдин ".repeat(count);
        assertTimeout(Duration.ofSeconds(5), () -> {
            for (String text : List.of(source, source + TOKEN)) {
                MystemOffsetAligner aligner = new MystemOffsetAligner(text);
                for (int index = 0; index < count; index++) {
                    assertEquals(new MystemTextRange(index * 6, index * 6 + 5), aligner.align(TOKEN));
                }
                assertTrue(aligner.issues().isEmpty());
            }
        });
    }

    private static String randomSurface(Random random, int maxLength) {
        String alphabet = "aab\u00AD\u00AD\uD83D\uDE00 ";
        StringBuilder surface = new StringBuilder();
        int length = random.nextInt(maxLength + 1);
        for (int index = 0; index < length; index++) {
            surface.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return surface.toString();
    }

    private static MystemTextRange referenceRange(String source, String token, int cursor) {
        // Deliberately slow oracle: enumerate source boundaries and compare complete candidates.
        // Inputs here are small; this fixes the prior alignment semantics independently of KMP.
        if (token.isEmpty()) {
            return new MystemTextRange(cursor, cursor);
        }
        int exact = source.indexOf(token, cursor);
        for (int start = cursor; start < source.length() && (exact < 0 || start < exact);
                start += Character.charCount(source.codePointAt(start))) {
            int end = start;
            int matched = 0;
            while (end < source.length() && matched < token.length()) {
                int value = source.codePointAt(end);
                if (value == SOFT_HYPHEN) {
                    end++;
                } else if (value == token.codePointAt(matched)) {
                    end += Character.charCount(value);
                    matched += Character.charCount(value);
                } else {
                    break;
                }
            }
            if (matched == token.length()) {
                return new MystemTextRange(start, end);
            }
        }
        return exact < 0 ? MystemTextRange.unknown() : new MystemTextRange(exact, exact + token.length());
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
