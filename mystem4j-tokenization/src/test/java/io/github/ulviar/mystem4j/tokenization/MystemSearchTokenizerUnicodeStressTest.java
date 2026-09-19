package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MystemSearchTokenizerUnicodeStressTest {
    @Test
    void tokenizesRandomUnicodeScalarsWithMonotonicOffsets() {
        Random random = new Random(0x4D_59_53_54);
        StringBuilder text = new StringBuilder();
        ArrayList<MystemToken> modelTokens = new ArrayList<>();

        for (int index = 0; index < 10_000; index++) {
            int codePoint = nextScalar(random);
            int start = text.length();
            text.appendCodePoint(codePoint);
            modelTokens.add(new MystemToken(text.substring(start), start, text.length(), List.of()));
        }

        List<MystemToken> sparseTokens = new ArrayList<>();
        for (int index = 0; index < modelTokens.size(); index += 3) {
            sparseTokens.add(modelTokens.get(index));
        }
        for (MystemSearchTokenizerOptions options : List.of(
                MystemSearchTokenizerOptions.conservative(),
                MystemSearchTokenizerOptions.search(),
                MystemSearchTokenizerOptions.entityAware())) {
            for (List<MystemToken> inputTokens : List.of(modelTokens, sparseTokens, List.<MystemToken>of())) {
                List<MystemSearchToken> tokens = new MystemSearchTokenizer(options)
                        .tokenize(new MystemDocument(text.toString(), inputTokens, List.of()));
                assertCompletePartition(text.toString(), tokens);
            }
        }
    }

    private static void assertCompletePartition(String text, List<MystemSearchToken> tokens) {
        int previousEnd = 0;
        StringBuilder reconstructed = new StringBuilder();
        for (MystemSearchToken token : tokens) {
            assertEquals(previousEnd, token.startOffset(), "gap or overlap in source partition");
            assertTrue(token.endOffset() >= token.startOffset());
            assertTrue(token.endOffset() <= text.length());
            assertTrue(token.endOffset() > token.startOffset());
            assertTrue(token.forms().size() > 0);
            assertFalse(token.forms().stream().anyMatch(form -> form.text().isEmpty()));
            assertEquals(text.substring(token.startOffset(), token.endOffset()), token.text());
            reconstructed.append(token.text());
            previousEnd = token.endOffset();
        }
        assertEquals(text.length(), previousEnd);
        assertEquals(text, reconstructed.toString());
    }

    private static int nextScalar(Random random) {
        while (true) {
            int codePoint = random.nextInt(Character.MAX_CODE_POINT + 1);
            if (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE) {
                return codePoint;
            }
        }
    }
}
