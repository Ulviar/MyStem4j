package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MystemModelMemorySmokeTest {
    @Test
    void repeatedLargeCleanTextPreprocessingAndParsingFitsSmallHeap() {
        MystemJsonParser parser = new MystemJsonParser();
        for (int iteration = 0; iteration < 300; iteration++) {
            String text = "а".repeat(64_000) + iteration;
            MystemPreparedText prepared = MystemTextPreprocessor.prepareJsonLine(text);
            MystemDocument document = parser.parse(prepared, jsonToken(text));

            assertEquals(1, document.tokens().size());
            assertEquals(text.length(), document.tokens().getFirst().endOffset());
        }
    }

    private static String jsonToken(String text) {
        return """
                [{"analysis":[],"text":"%s"}]
                """
                .formatted(text);
    }
}
