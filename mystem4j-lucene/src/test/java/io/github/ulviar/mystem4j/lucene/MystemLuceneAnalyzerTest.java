package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class MystemLuceneAnalyzerTest extends BaseTokenStreamTestCase {
    public void testEmitsFormsOffsetsPositionsTypesAndKeywordFlags() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"Раз++ C++".equals(input)) {
                return "[]";
            }
            return """
                    [
                      {"analysis":[{"lex":"раз++","gr":"S"}],"text":"Раз"},
                      {"analysis":[],"text":"C"}
                    ]
                    """;
        });
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.entityAware())) {
            assertAnalyzesTo(
                    analyzer,
                    "Раз++ C++",
                    new String[] {"раз++", "раз", "c++", "c"},
                    new int[] {0, 0, 6, 6},
                    new int[] {5, 5, 9, 9},
                    new String[] {"word", "word", "word", "word"},
                    new int[] {1, 0, 1, 0});
            assertKeywordFlags(analyzer, "Раз++ C++", true, true, false, false);
        }
    }

    public void testDefaultAnalyzerUsesConservativeSemanticPolicy() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"10# $ https://example.com".equals(input)) {
                return "[]";
            }
            return """
                    [
                      {"analysis":[],"text":"10#"},
                      {"analysis":[],"text":"https"},
                      {"analysis":[],"text":"example"},
                      {"analysis":[],"text":"com"}
                    ]
                    """;
        });
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            assertAnalyzesTo(
                    analyzer,
                    "10# $ https://example.com",
                    new String[] {"10#", "10", "https", "example", "com"},
                    new int[] {0, 0, 6, 14, 22},
                    new int[] {3, 3, 11, 21, 25},
                    new String[] {"word", "word", "word", "word", "word"},
                    new int[] {1, 0, 1, 1, 1});
            assertKeywordFlags(analyzer, "10# $ https://example.com", true, true, false, false, false);
        }
    }

    public void testPreparesUnsafeInputAndKeepsOriginalOffsets() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"A B".equals(input)) {
                return "[]";
            }
            return """
                    [
                      {"analysis":[],"text":"A"},
                      {"analysis":[],"text":"B"}
                    ]
                    """;
        });
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            assertAnalyzesTo(
                    analyzer,
                    "A\u0001B",
                    new String[] {"a", "b"},
                    new int[] {0, 2},
                    new int[] {1, 3},
                    new String[] {"word", "word"},
                    new int[] {1, 1});
            assertKeywordFlags(analyzer, "A\u0001B", false, false);
        }
    }

    public void testPreparesMultilineInputForJsonLineCompatibleClients() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0) {
                throw new AssertionError("client received multiline input: " + input);
            }
            if (!"A B".equals(input)) {
                return "[]";
            }
            return """
                    [
                      {"analysis":[],"text":"A"},
                      {"analysis":[],"text":"B"}
                    ]
                    """;
        });
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            assertAnalyzesTo(
                    analyzer,
                    "A\nB",
                    new String[] {"a", "b"},
                    new int[] {0, 2},
                    new int[] {1, 3},
                    new String[] {"word", "word"},
                    new int[] {1, 1});
        }
    }

    public void testTokenizerAnalyzesInputInBoundedChunks() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> input.isBlank()
                ? "[]"
                : """
                [{"analysis":[],"text":"%s"}]
                """.formatted(input.strip()));
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(100, 8, MystemLucenePositionPolicy.COMPACT);
        try (Analyzer analyzer =
                new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions)) {
            assertAnalyzesTo(
                    analyzer,
                    "Alpha Beta Gamma",
                    new String[] {"alpha", "beta", "gamma"},
                    new int[] {0, 6, 11},
                    new int[] {5, 10, 16},
                    new String[] {"word", "word", "word"},
                    new int[] {1, 1, 1});
        }

        assertEquals(List.of("Alpha ", "Beta ", "Gamma"), client.requests().subList(0, 3));
        for (String request : client.requests()) {
            assertTrue(request.length() <= analysisOptions.maxChunkChars());
        }
    }

    public void testPositionPolicyCanPreserveSkippedTokenGaps() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"A B".equals(input)) {
                return "[]";
            }
            return """
                    [
                      {"analysis":[],"text":"A"},
                      {"analysis":[],"text":"B"}
                    ]
                    """;
        });
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(100, 100, MystemLucenePositionPolicy.PRESERVE_SKIPPED_TOKENS);
        try (Analyzer analyzer =
                new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions)) {
            assertAnalyzesTo(
                    analyzer,
                    "A B",
                    new String[] {"a", "b"},
                    new int[] {0, 2},
                    new int[] {1, 3},
                    new String[] {"word", "word"},
                    new int[] {1, 2});
        }
    }

    public void testAnalyzerCanReuseComponentsForMultipleInputs() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> """
                [{"analysis":[],"text":"%s"}]
                """.formatted(input));
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            assertAnalyzesTo(
                    analyzer,
                    "Alpha",
                    new String[] {"alpha"},
                    new int[] {0},
                    new int[] {5},
                    new String[] {"word"},
                    new int[] {1});
            assertAnalyzesTo(
                    analyzer,
                    "Beta",
                    new String[] {"beta"},
                    new int[] {0},
                    new int[] {4},
                    new String[] {"word"},
                    new int[] {1});
        }
    }

    public void testOffsetsAreCorrectedThroughLuceneCharFilters() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"Alpha".equals(input)) {
                return "[]";
            }
            return """
                    [{"analysis":[],"text":"Alpha"}]
                    """;
        });
        try (Analyzer analyzer = new Analyzer() {
            @Override
            protected Reader initReader(String fieldName, Reader reader) {
                return new DropFirstCharacterFilter(reader);
            }

            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                return new TokenStreamComponents(new MystemLuceneTokenizer(client));
            }
        }) {
            assertAnalyzesTo(
                    analyzer,
                    "XAlpha",
                    new String[] {"alpha"},
                    new int[] {1},
                    new int[] {6},
                    new String[] {"word"},
                    new int[] {1});
        }
    }

    public void testEmptyInputProducesNoTokens() throws IOException {
        try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo())) {
            assertAnalyzesTo(analyzer, "", new String[0], new int[0], new int[0], new String[0], new int[0]);
        }
    }

    public void testRandomDataDoesNotBreakTokenStreamLifecycle() throws IOException {
        try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo())) {
            checkRandomData(random(), analyzer, 200, 256, false, false);
        }
    }

    public void testRejectsOversizedFieldsBeforeCallingClient() {
        FakeMystemClient client = FakeMystemClient.echo();
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), 4)) {
            expectThrows(IOException.class, () -> assertAnalyzesTo(analyzer, "12345", new String[0]));
        }
        assertTrue(client.requests().isEmpty());
    }

    public void testTokenizerReleasesBufferedFieldDataOnClose() throws Exception {
        String input = "A".repeat(20_000);
        FakeMystemClient client = new FakeMystemClient(request -> """
                [{"analysis":[],"text":"%s"}]
                """.formatted(request));
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(input.length(), input.length(), MystemLucenePositionPolicy.COMPACT);
        MystemLuceneTokenizer tokenizer =
                new MystemLuceneTokenizer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions);

        tokenizer.setReader(new StringReader(input));
        tokenizer.reset();
        while (tokenizer.incrementToken()) {
            // Consume the full stream.
        }
        tokenizer.end();

        StringBuilder pendingInput = privateField(tokenizer, "pendingInput", StringBuilder.class);
        assertTrue(pendingInput.capacity() > 1024);
        assertFalse(privateField(tokenizer, "emissions", List.class).isEmpty());

        tokenizer.close();

        assertEquals(0, pendingInput.length());
        assertTrue("closed tokenizer should release the large input buffer", pendingInput.capacity() <= 16);
        assertTrue(privateField(tokenizer, "emissions", List.class).isEmpty());
    }

    public void testRejectsInvalidLuceneAnalysisOptions() {
        expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneAnalysisOptions(0, 1, MystemLucenePositionPolicy.COMPACT));
        expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneAnalysisOptions(1, 0, MystemLucenePositionPolicy.COMPACT));
        expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneAnalysisOptions(1, 2, MystemLucenePositionPolicy.COMPACT));
        expectThrows(NullPointerException.class, () -> new MystemLuceneAnalysisOptions(1, 1, null));
    }

    public void testAnalyzerDoesNotCloseClientByDefault() {
        FakeMystemClient client = FakeMystemClient.echo();

        new MystemLuceneAnalyzer(client).close();

        assertFalse(client.isClosed());
    }

    public void testAnalyzerCanOwnClientWhenRequested() {
        FakeMystemClient client = FakeMystemClient.echo();

        new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), true).close();

        assertTrue(client.isClosed());
    }

    public void testAnalyzerClosesOwnedClientOnlyOnce() {
        FakeMystemClient client = FakeMystemClient.echo();
        Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), true);

        analyzer.close();
        analyzer.close();

        assertEquals(1, client.closeCount());
    }

    private static void assertKeywordFlags(Analyzer analyzer, String text, boolean... expected) throws IOException {
        try (TokenStream stream = analyzer.tokenStream("field", new StringReader(text))) {
            KeywordAttribute keyword = stream.addAttribute(KeywordAttribute.class);
            ArrayList<Boolean> actual = new ArrayList<>();
            stream.reset();
            while (stream.incrementToken()) {
                actual.add(keyword.isKeyword());
            }
            stream.end();
            assertEquals(toList(expected), actual);
        }
    }

    private static List<Boolean> toList(boolean... values) {
        ArrayList<Boolean> result = new ArrayList<>(values.length);
        for (boolean value : values) {
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static <T> T privateField(Object target, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static final class DropFirstCharacterFilter extends CharFilter {
        private String filtered;
        private int index;

        private DropFirstCharacterFilter(Reader input) {
            super(input);
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            ensureBuffered();
            if (index >= filtered.length()) {
                return -1;
            }
            int count = Math.min(len, filtered.length() - index);
            filtered.getChars(index, index + count, cbuf, off);
            index += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        protected int correct(int currentOff) {
            return currentOff + 1;
        }

        private void ensureBuffered() throws IOException {
            if (filtered != null) {
                return;
            }
            char[] buffer = new char[256];
            StringBuilder result = new StringBuilder();
            int read;
            while ((read = input.read(buffer)) != -1) {
                result.append(buffer, 0, read);
            }
            filtered = result.isEmpty() ? "" : result.substring(1);
        }
    }
}
