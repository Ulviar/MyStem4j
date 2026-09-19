package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MystemPreparedOffsetCompositionTest {
    @Test
    void everyBoundaryMatchesAnIndependentMapAcrossContractionsAndReplacements() {
        List<Fragment> fragments = new ArrayList<>(List.of(
                new Fragment("", "", "", List.of(0)),
                new Fragment("Аб", "Аб", "Аб", List.of(0, 1, 2)),
                new Fragment("😀", "😀", "😀", List.of(0, 1, 2)),
                new Fragment("e\u0301", "e\u0301", "e\u0301", List.of(0, 1, 2)),
                new Fragment("\r\n", "\r\n", "  ", List.of(0, 1, 2)),
                new Fragment("\t", "\t", "\t", List.of(0, 1)),
                new Fragment("\u0000\u0085", "  ", "  ", List.of(0, 1, 2)),
                new Fragment("x\uD800x", "x\uFFFDx", "x\uFFFDx", List.of(0, 1, 2, 3)),
                new Fragment("x\uDC00x", "x\uFFFDx", "x\uFFFDx", List.of(0, 1, 2, 3)),
                new Fragment("\uFDD0\uFFFF", "  ", "  ", List.of(0, 1, 2))));
        // All supplementary-plane noncharacters contract from two UTF-16 units to one space.
        for (int plane = 1; plane <= 16; plane++) {
            for (int tail : new int[] {0xFFFE, 0xFFFF}) {
                fragments.add(new Fragment(Character.toString((plane << 16) | tail), " ", " ", List.of(0, 2)));
            }
        }
        for (Fragment first : fragments) {
            for (Fragment second : fragments) {
                assertPrepared(List.of(first, second));
            }
        }
        Random random = new Random(0x4F4646534554L);
        for (int attempt = 0; attempt < 500; attempt++) {
            List<Fragment> sequence = new ArrayList<>();
            for (int index = 0; index < 30; index++) {
                sequence.add(fragments.get(random.nextInt(fragments.size())));
            }
            assertPrepared(sequence);
        }
    }

    private static void assertPrepared(List<Fragment> fragments) {
        StringBuilder original = new StringBuilder();
        StringBuilder normal = new StringBuilder();
        StringBuilder line = new StringBuilder();
        List<Integer> expectedOffsets = new ArrayList<>(List.of(0));
        for (Fragment fragment : fragments) {
            int base = original.length();
            original.append(fragment.original());
            normal.append(fragment.normal());
            line.append(fragment.line());
            for (int index = 1; index < fragment.offsets().size(); index++) {
                expectedOffsets.add(base + fragment.offsets().get(index));
            }
        }
        assertMap(original.toString(), normal.toString(), expectedOffsets,
                MystemTextPreprocessor.prepare(original.toString()));
        assertMap(original.toString(), line.toString(), expectedOffsets,
                MystemTextPreprocessor.prepareJsonLine(original.toString()));
    }

    private static void assertMap(
            String original, String expectedText, List<Integer> expectedOffsets, MystemPreparedText prepared) {
        assertEquals(original, prepared.originalText());
        assertEquals(expectedText, prepared.text());
        for (int index = 0; index < expectedOffsets.size(); index++) {
            assertEquals(expectedOffsets.get(index).intValue(), prepared.originalOffsetFor(index),
                    "prepared boundary " + index + " in " + expectedOffsets);
        }
    }

    private record Fragment(String original, String normal, String line, List<Integer> offsets) {}
}
