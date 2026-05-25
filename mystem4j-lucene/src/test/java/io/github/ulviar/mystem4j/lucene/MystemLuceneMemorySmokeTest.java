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
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class MystemLuceneMemorySmokeTest extends LuceneTestCase {
    public void testRepeatedLargeFieldAnalysisFitsSmallHeap() throws IOException {
        MystemLuceneAnalysisOptions analysisOptions =
                new MystemLuceneAnalysisOptions(128_000, 128_000, MystemLucenePositionPolicy.COMPACT);
        try (Analyzer analyzer = new MystemLuceneAnalyzer(
                new NonRetainingEchoClient(), MystemSearchTokenizerOptions.conservative(), analysisOptions)) {
            for (int iteration = 0; iteration < 200; iteration++) {
                String text = largeToken(iteration);
                assertEquals(text.length(), consumeSingleToken(analyzer, text));
            }
        }
    }

    private static int consumeSingleToken(Analyzer analyzer, String text) throws IOException {
        try (TokenStream stream = analyzer.tokenStream("body", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            assertTrue(stream.incrementToken());
            int length = term.length();
            assertFalse(stream.incrementToken());
            stream.end();
            return length;
        }
    }

    private static String largeToken(int iteration) {
        return "a".repeat(64_000) + iteration;
    }

    private static final class NonRetainingEchoClient implements MystemClient {
        @Override
        public MystemRawResult analyze(String text) {
            String output = """
                    [{"analysis":[],"text":"%s"}]
                    """
                    .formatted(text);
            return new MystemRawResult(
                    text,
                    output,
                    MystemOutputFormat.JSON,
                    new MystemRequestStats(
                            Duration.ZERO,
                            MystemExecutionMode.ONE_SHOT_TEXT,
                            text.length(),
                            -1,
                            output.length(),
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
