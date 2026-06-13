package io.github.ulviar.mystem4j.tokenization;

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

        List<MystemSearchToken> tokens = new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware())
                .tokenize(new MystemDocument(text.toString(), modelTokens, List.of()));

        int previousStart = 0;
        int previousEnd = 0;
        for (MystemSearchToken token : tokens) {
            assertTrue(token.startOffset() >= previousStart);
            assertTrue(token.startOffset() >= previousEnd);
            assertTrue(token.endOffset() >= token.startOffset());
            assertTrue(token.endOffset() <= text.length());
            assertTrue(token.endOffset() > token.startOffset());
            assertTrue(token.forms().size() > 0);
            assertFalse(token.forms().stream().anyMatch(form -> form.text().isEmpty()));
            assertTrue(text.substring(token.startOffset(), token.endOffset()).equals(token.text()));
            previousStart = token.startOffset();
            previousEnd = token.endOffset();
        }
        assertTrue(previousEnd <= text.length());
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
