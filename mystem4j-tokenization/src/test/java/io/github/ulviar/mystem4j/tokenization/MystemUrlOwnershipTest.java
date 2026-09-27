package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemUrlOwnershipTest {
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void nestedSchemesInOneValidUrlHaveBoundedWork() {
        String source = "https://example.com/" + "http://a/".repeat(16_000);
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<MystemSearchToken> tokens = tokenize(source);
            assertEquals(1, tokens.size());
            assertEquals(MystemSearchTokenType.URL, tokens.getFirst().type());
            assertEquals(source, tokens.getFirst().text());
            assertEquals(List.of(new MystemTokenForm(source, true), new MystemTokenForm("example.com", true)),
                    tokens.getFirst().forms());
            assertPartition(source, tokens);
        });
    }

    @Test
    void malformedNestedUrlTailHasBoundedWorkAndNoSalvagedUrl() {
        String source = "https://example.com/" + "http://a/".repeat(16_000) + "%";
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<MystemSearchToken> tokens = tokenize(source);
            assertTrue(urls(tokens).isEmpty());
            assertPartition(source, tokens);
        });
    }

    @Test
    void manyAdjacentEntitiesHaveBoundedWorkAndRemainSeparate() {
        String entity = "https://example.com/a(b)c";
        String source = String.join(",", Collections.nCopies(16_000, entity));
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<MystemSearchToken> tokens = tokenize(source);
            List<MystemSearchToken> urls = urls(tokens);
            assertEquals(16_000, urls.size());
            assertTrue(urls.stream().allMatch(token -> token.text().equals(entity)));
            assertPartition(source, tokens);
        });
    }

    @Test
    void rejectedUrlShapedSpansOwnEmbeddedSchemesButNotSeparateEntities() {
        for (String malformed : List.of("https://bad:99999/path/http://good.test/path",
                "https://example.com/%http://good.test/path")) {
            assertTrue(urls(tokenize(malformed)).isEmpty(), malformed);
            for (String delimiter : List.of(" ", ",", ";")) {
                String source = malformed + delimiter + "https://other.test/a(b)c";
                List<MystemSearchToken> tokens = tokenize(source);
                assertEquals(List.of("https://other.test/a(b)c"),
                        urls(tokens).stream().map(MystemSearchToken::text).toList(), source);
                assertPartition(source, tokens);
            }
        }
    }

    @Test
    void onlyRecognizedAdjacentEntitiesBoundAValidUrl() {
        String literal = "https://example.com/path,http://bad:99999/path";
        assertEquals(List.of(literal), urls(tokenize(literal)).stream().map(MystemSearchToken::text).toList());
        String source = literal + ";https://other.test/path,a@third.test";
        List<MystemSearchToken> tokens = tokenize(source);
        assertEquals(List.of(literal, "https://other.test/path"),
                urls(tokens).stream().map(MystemSearchToken::text).toList());
        assertEquals(List.of("a@third.test"), tokens.stream()
                .filter(token -> token.type() == MystemSearchTokenType.EMAIL).map(MystemSearchToken::text).toList());
        assertPartition(source, tokens);
    }

    private List<MystemSearchToken> tokenize(String source) {
        return tokenizer.tokenize(new MystemDocument(source, List.of(), List.of()));
    }

    private static List<MystemSearchToken> urls(List<MystemSearchToken> tokens) {
        return tokens.stream().filter(token -> token.type() == MystemSearchTokenType.URL).toList();
    }

    private static void assertPartition(String source, List<MystemSearchToken> tokens) {
        int cursor = 0;
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset());
            assertTrue(token.endOffset() > token.startOffset());
            assertEquals(source.substring(token.startOffset(), token.endOffset()), token.text());
            cursor = token.endOffset();
        }
        assertEquals(source.length(), cursor);
    }
}
