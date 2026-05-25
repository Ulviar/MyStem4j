package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
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

    public void testSplitsMultilineInputForJsonLineCompatibleClients() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> switch (input) {
            case "A" -> """
                    [{"analysis":[],"text":"A"}]
                    """;
            case "B" -> """
                    [{"analysis":[],"text":"B"}]
                    """;
            default -> {
                if (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0) {
                    throw new AssertionError("client received multiline input: " + input);
                }
                yield "[]";
            }
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
            checkRandomData(random(), analyzer, 50, 64, false, false);
        }
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
