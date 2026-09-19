package consumer;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestStats;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

public final class Smoke {
    public static void main(String[] args) throws IOException {
        try (MystemClient client = new JsonClient();
                var analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.conservative());
                var stream = analyzer.tokenStream("body", "Мамы")) {
            var term = stream.addAttribute(CharTermAttribute.class);
            var offsets = stream.addAttribute(OffsetAttribute.class);
            stream.reset();
            if (!stream.incrementToken() || !term.toString().equals("мама")
                    || offsets.startOffset() != 0 || offsets.endOffset() != 4 || stream.incrementToken()) {
                throw new AssertionError("Unexpected Lucene token stream");
            }
            stream.end();
        }
    }

    private static final class JsonClient implements MystemClient {
        @Override
        public MystemRawResult analyze(String text) {
            String json = "[{\"text\":\"Мамы\",\"analysis\":[{\"lex\":\"мама\",\"gr\":\"S\"}]}]";
            return new MystemRawResult(text, json, MystemOutputFormat.JSON,
                    new MystemRequestStats(Duration.ZERO, MystemExecutionMode.ONE_SHOT_TEXT,
                            text.length(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                            json.length(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
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
