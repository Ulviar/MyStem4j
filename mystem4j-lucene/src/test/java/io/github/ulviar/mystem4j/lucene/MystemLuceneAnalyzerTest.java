package io.github.ulviar.mystem4j.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestStats;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.junit.jupiter.api.Test;

class MystemLuceneAnalyzerTest {
    @Test
    void emitsFormsOffsetsPositionsTypesAndKeywordFlags() throws IOException {
        FakeClient client = new FakeClient(input -> """
                [
                  {"analysis":[{"lex":"раз++","gr":"S"}],"text":"Раз"},
                  {"analysis":[],"text":"C"}
                ]
                """);
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.entityAware())) {
            List<SeenToken> tokens = analyze(analyzer, "Раз++ C++");

            assertEquals(List.of(
                    new SeenToken("раз++", 0, 5, 1, "word", true),
                    new SeenToken("раз", 0, 5, 0, "word", true),
                    new SeenToken("c++", 6, 9, 1, "word", false),
                    new SeenToken("c", 6, 9, 0, "word", false)), tokens);
        }
    }

    @Test
    void defaultAnalyzerUsesConservativeSemanticPolicy() throws IOException {
        FakeClient client = new FakeClient(input -> """
                [
                  {"analysis":[],"text":"10#"},
                  {"analysis":[],"text":"$"},
                  {"analysis":[],"text":"https"},
                  {"analysis":[],"text":"://"},
                  {"analysis":[],"text":"example"},
                  {"analysis":[],"text":"."},
                  {"analysis":[],"text":"com"}
                ]
                """);
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            List<SeenToken> tokens = analyze(analyzer, "10# $ https://example.com");

            assertEquals(List.of(
                    new SeenToken("10#", 0, 3, 1, "word", true),
                    new SeenToken("10", 0, 3, 0, "word", true),
                    new SeenToken("https", 6, 11, 1, "word", false),
                    new SeenToken("example", 14, 21, 1, "word", false),
                    new SeenToken("com", 22, 25, 1, "word", false)), tokens);
            assertFalse(tokens.stream().anyMatch(token -> token.type().equals("url")));
            assertFalse(tokens.stream().anyMatch(token -> token.type().equals("currency")));
            assertFalse(tokens.stream().anyMatch(token -> token.type().equals("number")));
        }
    }

    @Test
    void preparesUnsafeInputAndKeepsOriginalOffsets() throws IOException {
        FakeClient client = new FakeClient(input -> {
            assertEquals("A B", input);
            return """
                    [
                      {"analysis":[],"text":"A"},
                      {"analysis":[],"text":"B"}
                    ]
                    """;
        });
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            List<SeenToken> tokens = analyze(analyzer, "A\u0001B");

            assertEquals(List.of(
                    new SeenToken("a", 0, 1, 1, "word", false),
                    new SeenToken("b", 2, 3, 1, "word", false)), tokens);
        }
    }

    private static List<SeenToken> analyze(Analyzer analyzer, String text) throws IOException {
        try (TokenStream stream = analyzer.tokenStream("field", new StringReader(text))) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = stream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrement = stream.addAttribute(PositionIncrementAttribute.class);
            TypeAttribute type = stream.addAttribute(TypeAttribute.class);
            KeywordAttribute keyword = stream.addAttribute(KeywordAttribute.class);

            ArrayList<SeenToken> result = new ArrayList<>();
            stream.reset();
            while (stream.incrementToken()) {
                result.add(new SeenToken(
                        term.toString(),
                        offset.startOffset(),
                        offset.endOffset(),
                        positionIncrement.getPositionIncrement(),
                        type.type(),
                        keyword.isKeyword()));
            }
            stream.end();
            return List.copyOf(result);
        }
    }

    private record SeenToken(
            String term, int startOffset, int endOffset, int positionIncrement, String type, boolean keyword) {}

    private static final class FakeClient implements MystemClient {
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
}
