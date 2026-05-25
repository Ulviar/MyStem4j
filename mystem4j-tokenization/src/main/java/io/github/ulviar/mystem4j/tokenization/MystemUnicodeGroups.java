package io.github.ulviar.mystem4j.tokenization;

import java.util.Set;

final class MystemUnicodeGroups {
    static final CodePointGroup NON_SPACING_MARKS_NEW = CodePointGroup.of(
            Set.of(2045, 2558, 2901, 3076, 3132, 3328, 3457, 3770, 3790, 6159, 43052, 43263),
            ranges(range(2200, 2259), range(2810, 2815), range(3387, 3388), range(6847, 6862), range(7670, 7674)),
            65071);
    static final CodePointGroup NON_SPACING_MARKS_KHMER = CodePointGroup.of(ranges(range(6068, 6069)));
    static final CodePointGroup COMBINING_SPACING_MARKS_NEW =
            CodePointGroup.of(Set.of(3315, 5909, 7415), ranges(), 44012);
    static final CodePointGroup LETTER_NUMBERS_RUSSIAN_HOMOGLYPH_ROMAN = CodePointGroup.of(
            Set.of(8544, 8557, 8573), ranges(range(8553, 8554), range(8559, 8560), range(8569, 8570)));
    static final CodePointGroup LETTER_NUMBERS_NEW = CodePointGroup.of(Set.of(), ranges(), 42735);
    static final CodePointGroup OTHER_NUMBERS_NEW = CodePointGroup.of(Set.of(), ranges(), 43061);
    static final CodePointGroup OTHER_PUNCTUATION_NUMBER_PARTS = CodePointGroup.of(
            Set.of(46, 894, 903, 1417, 1567, 1748, 4962, 4967, 5742, 6145, 6147, 6153, 8230, 65106),
            ranges(
                    range(1793, 1794),
                    range(2404, 2405),
                    range(6100, 6101),
                    range(6468, 6469),
                    range(8252, 8253),
                    range(8263, 8265),
                    range(65110, 65111)));
    static final CodePointGroup OTHER_PUNCTUATION_ARMENIAN =
            CodePointGroup.of(Set.of(1374), ranges(range(1371, 1372)));
    static final CodePointGroup OTHER_SYMBOL_ARABIC_START_OF_RUB_EL_HIZB = CodePointGroup.of(Set.of(1758));

    private MystemUnicodeGroups() {}

    private static int[] range(int startInclusive, int endInclusive) {
        return new int[] {startInclusive, endInclusive};
    }

    private static int[][] ranges(int[]... ranges) {
        return ranges;
    }

    static final class CodePointGroup {
        private final Set<Integer> codePoints;
        private final int[][] ranges;
        private final int threshold;

        private CodePointGroup(Set<Integer> codePoints, int[][] ranges, int threshold) {
            this.codePoints = Set.copyOf(codePoints);
            this.ranges = ranges.clone();
            this.threshold = threshold;
        }

        static CodePointGroup of(Set<Integer> codePoints) {
            return new CodePointGroup(codePoints, ranges(), -1);
        }

        static CodePointGroup of(int[][] ranges) {
            return new CodePointGroup(Set.of(), ranges, -1);
        }

        static CodePointGroup of(Set<Integer> codePoints, int[][] ranges) {
            return new CodePointGroup(codePoints, ranges, -1);
        }

        static CodePointGroup of(Set<Integer> codePoints, int[][] ranges, int threshold) {
            return new CodePointGroup(codePoints, ranges, threshold);
        }

        boolean contains(int codePoint) {
            if (threshold >= 0 && codePoint > threshold) {
                return true;
            }
            if (codePoints.contains(codePoint)) {
                return true;
            }
            for (int[] range : ranges) {
                if (codePoint >= range[0] && codePoint <= range[1]) {
                    return true;
                }
            }
            return false;
        }
    }
}
