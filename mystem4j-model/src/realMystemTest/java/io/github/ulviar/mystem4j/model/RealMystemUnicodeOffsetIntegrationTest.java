package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealMystemUnicodeOffsetIntegrationTest {
    private static final int DEFAULT_STRESS_CHUNK_SIZE = 2_048;

    private final MystemJsonParser parser = new MystemJsonParser();

    @Test
    void preservesOffsetsForRepresentativeUnicodeCorpusWithRealMystem() {
        int[] codePoints = {
            0x0000,
            0x0009,
            0x000A,
            0x000D,
            0x001F,
            0x007F,
            0x00A0,
            0x0301,
            0x034F,
            0x200B,
            0x200D,
            0x2028,
            0x2060,
            0x3000,
            0xE000,
            0xFDD0,
            0xFE0F,
            0x1F600,
            0x1F1E6,
            0xE0100,
            0x10FFFF
        };

        try (MystemClient client = newClient()) {
            assertCorpusOffsets(client, Arrays.stream(codePoints).boxed().toList(), "representative Unicode corpus");
        }
    }

    @Test
    void preservesOffsetsForSoftHyphensDroppedByRealMystem() {
        try (MystemClient client = newClient()) {
            String originalText = "О\u00ADд\u00ADи\u00ADн";
            MystemPreparedText prepared = MystemTextPreprocessor.prepare(originalText);
            MystemRawResult rawResult = client.analyze(prepared.text());
            MystemDocument document = parser.parse(prepared, rawResult.output());

            assertTrue(document.issues().stream()
                    .noneMatch(issue -> issue.type() == MystemTextIssueType.UNMATCHED_TOKEN));
            assertEquals(1, document.tokens().size());
            assertEquals(0, document.tokens().get(0).startOffset());
            assertEquals(originalText.length(), document.tokens().get(0).endOffset());
        }
    }

    @Test
    void stressChecksAllUnicodeScalarValuesWithRealMystem() {
        assumeTrue(Boolean.getBoolean("mystem4j.unicodeStress"), "Set -Dmystem4j.unicodeStress=true to run.");
        int chunkSize = Math.max(1, Integer.getInteger("mystem4j.unicodeStressChunkSize", DEFAULT_STRESS_CHUNK_SIZE));

        try (MystemClient client = newClient()) {
            ArrayList<Integer> chunk = new ArrayList<>(chunkSize);
            for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
                if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                    continue;
                }
                chunk.add(codePoint);
                if (chunk.size() == chunkSize) {
                    assertCorpusOffsets(client, chunk, chunkLabel(chunk));
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                assertCorpusOffsets(client, chunk, chunkLabel(chunk));
            }
        }
    }

    private static MystemClient newClient() {
        return Mystem.builder()
                .executable(Path.of(System.getProperty("mystem4j.executable")))
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .build())
                .requestTimeout(Duration.ofSeconds(30))
                .maxRequestChars(2_000_000)
                .maxRequestBytes(8_000_000)
                .maxResponseChars(16_000_000)
                .maxResponseBytes(64_000_000)
                .build();
    }

    private void assertCorpusOffsets(MystemClient client, List<Integer> codePoints, String label) {
        String originalText = corpusFor(codePoints);
        MystemPreparedText prepared = MystemTextPreprocessor.prepare(originalText);
        MystemRawResult rawResult = client.analyze(prepared.text());
        MystemDocument document = parser.parse(prepared, rawResult.output());

        List<MystemTextIssue> unmatched = document.issues().stream()
                .filter(issue -> issue.type() == MystemTextIssueType.UNMATCHED_TOKEN)
                .toList();
        assertTrue(unmatched.isEmpty(), () -> label + " has unmatched tokens: " + unmatched);

        for (MystemToken token : document.tokens()) {
            if (!token.hasKnownOffsets()) {
                fail(label + " has a token without offsets: " + token);
            }
            String originalSlice = document.originalText().substring(token.startOffset(), token.endOffset());
            assertEquals(originalSlice, token.text(), () -> label + " token text differs at offsets "
                    + token.startOffset() + ".." + token.endOffset());
        }
    }

    private static String corpusFor(List<Integer> codePoints) {
        StringBuilder builder = new StringBuilder(codePoints.size() * 16);
        builder.append("начало ");
        for (int codePoint : codePoints) {
            builder.appendCodePoint(codePoint).append(' ');
        }
        builder.append("конец");
        return builder.toString();
    }

    private static String chunkLabel(List<Integer> codePoints) {
        return "Unicode chunk U+" + Integer.toHexString(codePoints.get(0)).toUpperCase()
                + "..U+" + Integer.toHexString(codePoints.get(codePoints.size() - 1)).toUpperCase();
    }
}
