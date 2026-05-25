package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestStats;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class MystemLuceneAnalyzerTest extends BaseTokenStreamTestCase {
    public void testEmitsFormsOffsetsPositionsTypesAndKeywordFlags() throws IOException {
        FakeClient client = new FakeClient(input -> {
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
        FakeClient client = new FakeClient(input -> {
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
        FakeClient client = new FakeClient(input -> {
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

    public void testAnalyzerCanReuseComponentsForMultipleInputs() throws IOException {
        FakeClient client = new FakeClient(input -> """
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
        FakeClient client = new FakeClient(input -> {
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
        try (Analyzer analyzer = new MystemLuceneAnalyzer(new EchoClient())) {
            assertAnalyzesTo(analyzer, "", new String[0], new int[0], new int[0], new String[0], new int[0]);
        }
    }

    public void testRandomDataDoesNotBreakTokenStreamLifecycle() throws IOException {
        try (Analyzer analyzer = new MystemLuceneAnalyzer(new EchoClient())) {
            checkRandomData(random(), analyzer, 50, 64, false, false);
        }
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

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

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

    private static class FakeClient implements MystemClient {
        private final Function<String, String> output;

        private FakeClient(Function<String, String> output) {
            this.output = output;
        }

        @Override
        public MystemRawResult analyze(String text) {
            String rawOutput = output.apply(text);
            return new MystemRawResult(
                    text,
                    rawOutput,
                    MystemOutputFormat.JSON,
                    new MystemRequestStats(
                            Duration.ZERO,
                            MystemExecutionMode.ONE_SHOT_TEXT,
                            text.length(),
                            -1,
                            rawOutput.length(),
                            -1,
                            OptionalInt.empty(),
                            false));
        }

        @Override
        public MystemFileContentResult analyzeFile(Path input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MystemFileResult analyzeFile(Path input, Path output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }

    private static final class EchoClient extends FakeClient {
        private EchoClient() {
            super(input -> input.isEmpty()
                    ? "[]"
                    : """
                    [{"analysis":[],"text":%s}]
                    """.formatted(jsonString(input)));
        }
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        appendUnicodeEscape(result, character);
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        result.append('"');
        return result.toString();
    }

    private static void appendUnicodeEscape(StringBuilder result, char character) {
        result.append("\\u");
        result.append(HEX_DIGITS[(character >>> 12) & 0xF]);
        result.append(HEX_DIGITS[(character >>> 8) & 0xF]);
        result.append(HEX_DIGITS[(character >>> 4) & 0xF]);
        result.append(HEX_DIGITS[character & 0xF]);
    }
}
