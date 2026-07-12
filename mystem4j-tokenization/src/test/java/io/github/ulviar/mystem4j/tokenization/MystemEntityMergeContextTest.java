package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemEntityMergeContextTest {
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void mergesUrlsWithoutConsumingAdjacentTextOrPunctuation() {
        List<EntityCase> cases = List.of(
                new EntityCase("пиши,https://example.com", "https://example.com", "example.com"),
                new EntityCase("[https://example.com]", "https://example.com", "example.com"),
                new EntityCase("https://example.com,дальше", "https://example.com", "example.com"),
                new EntityCase(
                        "до https://example.com/path?q=1#part после",
                        "https://example.com/path?q=1#part",
                        "example.com"));

        for (EntityCase testCase : cases) {
            List<MystemSearchToken> tokens = tokenizeOriginalText(testCase.input());
            MystemSearchToken entity = onlyTokenOfType(tokens, MystemSearchTokenType.URL, testCase.input());

            assertEquals(testCase.entityText(), entity.text(), testCase.input());
            assertEquals(testCase.entityText(),
                    testCase.input().substring(entity.startOffset(), entity.endOffset()),
                    testCase.input());
            assertTrue(entity.forms().contains(new MystemTokenForm(testCase.domain(), true)), testCase.input());
            assertPartition(testCase.input(), tokens);
        }
    }

    @Test
    void mergesEmailsWithoutConsumingAdjacentTextOrPunctuation() {
        List<EntityCase> cases = List.of(
                new EntityCase("пиши,me@example.com", "me@example.com", "example.com"),
                new EntityCase("[me@example.com]", "me@example.com", "example.com"),
                new EntityCase("me@example.com,дальше", "me@example.com", "example.com"),
                new EntityCase("до first.last+tag@example.com после", "first.last+tag@example.com", "example.com"));

        for (EntityCase testCase : cases) {
            List<MystemSearchToken> tokens = tokenizeOriginalText(testCase.input());
            MystemSearchToken entity = onlyTokenOfType(tokens, MystemSearchTokenType.EMAIL, testCase.input());

            assertEquals(testCase.entityText(), entity.text(), testCase.input());
            assertEquals(testCase.entityText(),
                    testCase.input().substring(entity.startOffset(), entity.endOffset()),
                    testCase.input());
            assertTrue(entity.forms().contains(new MystemTokenForm(testCase.domain(), true)), testCase.input());
            assertPartition(testCase.input(), tokens);
        }
    }

    @Test
    void leavesInvalidEntityCandidatesUnmerged() {
        for (String input : List.of("not-a-url://", "https://", "invalid@@example.com", "me@example")) {
            List<MystemSearchToken> tokens = tokenizeOriginalText(input);

            assertFalse(tokens.stream().anyMatch(token -> token.type() == MystemSearchTokenType.URL), input);
            assertFalse(tokens.stream().anyMatch(token -> token.type() == MystemSearchTokenType.EMAIL), input);
            assertPartition(input, tokens);
        }
    }

    private List<MystemSearchToken> tokenizeOriginalText(String input) {
        return tokenizer.tokenize(new MystemDocument(input, List.of(), List.of()));
    }

    private static MystemSearchToken onlyTokenOfType(
            List<MystemSearchToken> tokens, MystemSearchTokenType type, String input) {
        List<MystemSearchToken> matching = tokens.stream().filter(token -> token.type() == type).toList();
        assertEquals(1, matching.size(), () -> "Expected one " + type + " for " + input + ": " + tokens);
        return matching.getFirst();
    }

    private static void assertPartition(String input, List<MystemSearchToken> tokens) {
        int cursor = 0;
        StringBuilder reconstructed = new StringBuilder(input.length());
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset(), () -> "Gap or overlap for " + input + ": " + tokens);
            assertTrue(token.endOffset() > token.startOffset(), () -> "Empty token for " + input + ": " + token);
            assertEquals(
                    input.substring(token.startOffset(), token.endOffset()),
                    token.text(),
                    () -> "Invalid token slice for " + input + ": " + token);
            reconstructed.append(token.text());
            cursor = token.endOffset();
        }
        assertEquals(input.length(), cursor, () -> "Uncovered suffix for " + input + ": " + tokens);
        assertEquals(input, reconstructed.toString());
    }

    private record EntityCase(String input, String entityText, String domain) {}
}
