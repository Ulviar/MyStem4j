package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClientExecutionProfile;
import io.github.ulviar.mystem4j.MystemOutputFormat;
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
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
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

    public void testChunkingDoesNotSplitSurrogatePairs() throws IOException {
        FakeMystemClient client = FakeMystemClient.echo();
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(16, 1, MystemLucenePositionPolicy.COMPACT);
        try (Analyzer analyzer =
                new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions)) {
            assertAnalyzesTo(
                    analyzer,
                    "😀 A",
                    new String[] {"a"},
                    new int[] {3},
                    new int[] {4},
                    new String[] {"word"},
                    new int[] {1});
        }

        assertTrue(client.requests().contains("😀"));
        for (String request : client.requests()) {
            assertFalse("request contains an unpaired surrogate: " + request, containsUnpairedSurrogate(request));
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

    public void testPositionPolicyPreservesTrailingSkippedTokenGapInEndState() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"A ".equals(input)) {
                return "[]";
            }
            return """
                    [{"analysis":[],"text":"A"}]
                    """;
        });
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(100, 100, MystemLucenePositionPolicy.PRESERVE_SKIPPED_TOKENS);
        try (Analyzer analyzer =
                new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions);
                TokenStream stream = analyzer.tokenStream("field", new StringReader("A "))) {
            PositionIncrementAttribute positionIncrement = stream.addAttribute(PositionIncrementAttribute.class);
            stream.reset();
            assertTrue(stream.incrementToken());
            assertEquals(1, positionIncrement.getPositionIncrement());
            assertFalse(stream.incrementToken());
            stream.end();
            assertEquals(1, positionIncrement.getPositionIncrement());
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
            checkRandomData(random(), analyzer, 200, 256, true, true);
        }
    }

    public void testRejectsOversizedFieldsBeforeCallingClient() {
        FakeMystemClient client = FakeMystemClient.echo();
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), 4)) {
            expectThrows(IOException.class, () -> assertAnalyzesTo(analyzer, "12345", new String[0]));
        }
        assertTrue(client.requests().isEmpty());
    }

    public void testCanTruncateOversizedFieldsAtConfiguredLimit() throws IOException {
        FakeMystemClient client = FakeMystemClient.echo();
        MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
                4,
                4,
                MystemLucenePositionPolicy.COMPACT,
                MystemLuceneClientPolicy.ALLOW_ANY,
                MystemLuceneOversizedInputPolicy.TRUNCATE_AT_CODE_POINT_BOUNDARY);

        try (Analyzer analyzer =
                new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions)) {
            assertAnalyzesTo(
                    analyzer,
                    "Alpha",
                    new String[] {"alph"},
                    new int[] {0},
                    new int[] {4},
                    new String[] {"word"},
                    new int[] {1});
        }

        assertFalse(client.requests().isEmpty());
        assertTrue(client.requests().contains("Alph"));
        assertTrue(client.requests().stream().allMatch(request -> request.length() <= analysisOptions.maxInputChars()));
    }

    public void testTruncationDoesNotLeaveUnpairedSurrogateAtLimit() throws IOException {
        FakeMystemClient client = FakeMystemClient.echo();
        MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
                2,
                2,
                MystemLucenePositionPolicy.COMPACT,
                MystemLuceneClientPolicy.ALLOW_ANY,
                MystemLuceneOversizedInputPolicy.TRUNCATE_AT_CODE_POINT_BOUNDARY);

        try (Analyzer analyzer =
                new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions)) {
            assertAnalyzesTo(
                    analyzer,
                    "A😀B",
                    new String[] {"a"},
                    new int[] {0},
                    new int[] {1},
                    new String[] {"word"},
                    new int[] {1});
        }

        assertFalse(client.requests().isEmpty());
        assertTrue(client.requests().contains("A"));
        assertTrue(client.requests().stream().noneMatch(MystemLuceneAnalyzerTest::containsUnpairedSurrogate));
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
        expectThrows(
                NullPointerException.class,
                () -> new MystemLuceneAnalysisOptions(1, 1, MystemLucenePositionPolicy.COMPACT, null));
        expectThrows(
                NullPointerException.class,
                () -> new MystemLuceneAnalysisOptions(
                        1,
                        1,
                        MystemLucenePositionPolicy.COMPACT,
                        MystemLuceneClientPolicy.ALLOW_ANY,
                        null));
    }

    public void testStrictClientPolicyRejectsOneShotRuntimeClient() {
        FakeMystemClient client = new FakeMystemClient(
                input -> "[]", MystemClientExecutionProfile.ONE_SHOT_PROCESS_PER_REQUEST);
        MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
                100,
                100,
                MystemLucenePositionPolicy.COMPACT,
                MystemLuceneClientPolicy.REQUIRE_POOLED_OR_UNKNOWN);

        IllegalArgumentException error = expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions));

        assertTrue(error.getMessage().contains("one-shot MyStem client"));
    }

    public void testStrictClientPolicyRejectsReusableRuntimeClient() {
        FakeMystemClient client = new FakeMystemClient(input -> "[]", MystemClientExecutionProfile.REUSABLE_SESSION);
        MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
                100,
                100,
                MystemLucenePositionPolicy.COMPACT,
                MystemLuceneClientPolicy.REQUIRE_POOLED_OR_UNKNOWN);

        IllegalArgumentException error = expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative(), analysisOptions));

        assertTrue(error.getMessage().contains("reusable-session MyStem client"));
    }

    public void testStrictClientPolicyAllowsUnknownAndPooledProfiles() {
        MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
                100,
                100,
                MystemLucenePositionPolicy.COMPACT,
                MystemLuceneClientPolicy.REQUIRE_POOLED_OR_UNKNOWN);

        new MystemLuceneAnalyzer(FakeMystemClient.echo(), MystemSearchTokenizerOptions.conservative(), analysisOptions)
                .close();
        new MystemLuceneAnalyzer(
                        new FakeMystemClient(input -> "[]", MystemClientExecutionProfile.POOLED_SESSIONS),
                        MystemSearchTokenizerOptions.conservative(),
                        analysisOptions)
                .close();
    }

    public void testRejectsNullClientExecutionProfile() {
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(100, 100, MystemLucenePositionPolicy.COMPACT);

        expectThrows(
                NullPointerException.class,
                () -> new MystemLuceneAnalyzer(
                        new FakeMystemClient(input -> "[]", null),
                        MystemSearchTokenizerOptions.conservative(),
                        analysisOptions));
    }

    public void testAnalyzerRejectsKnownNonJsonClientFormat() {
        FakeMystemClient client = FakeMystemClient.withOutputFormat(input -> "plain text", MystemOutputFormat.TEXT);

        IllegalArgumentException error = expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative()));

        assertTrue(error.getMessage().contains("requires a MyStem client configured for JSON output"));
        assertTrue(error.getMessage().contains("TEXT"));
    }

    public void testTokenizerRejectsKnownNonJsonClientFormat() {
        FakeMystemClient client = FakeMystemClient.withOutputFormat(input -> "plain text", MystemOutputFormat.TEXT);

        IllegalArgumentException error = expectThrows(
                IllegalArgumentException.class,
                () -> new MystemLuceneTokenizer(client, MystemSearchTokenizerOptions.conservative()));

        assertTrue(error.getMessage().contains("requires a MyStem client configured for JSON output"));
        assertTrue(error.getMessage().contains("TEXT"));
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

    private static boolean containsUnpairedSurrogate(String text) {
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
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
