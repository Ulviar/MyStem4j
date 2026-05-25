package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemSearchTokenizerMemorySmokeTest {
    @Test
    void repeatedLargeTokenClassificationFitsSmallHeap() {
        MystemSearchTokenizer tokenizer = new MystemSearchTokenizer();
        for (int iteration = 0; iteration < 500; iteration++) {
            String text = "a".repeat(64_000) + iteration;
            MystemDocument document =
                    new MystemDocument(text, List.of(new MystemToken(text, 0, text.length(), List.of())), List.of());

            List<MystemSearchToken> tokens = tokenizer.tokenize(document);

            assertEquals(1, tokens.size());
            assertEquals(text.length(), tokens.getFirst().endOffset());
        }
    }
}
