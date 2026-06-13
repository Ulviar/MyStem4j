package io.github.ulviar.mystem4j.benchmark;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestStats;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemTextPreprocessor;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class MystemCoreBenchmark {
    @Benchmark
    public Object parseJson(SampleData data) {
        return data.parser.parse(data.text, data.json);
    }

    @Benchmark
    public Object preprocessUnicode(SampleData data) {
        return MystemTextPreprocessor.prepareJsonLine(data.unicodeText);
    }

    @Benchmark
    public Object tokenizeSearchTerms(SampleData data) {
        return data.tokenizer.tokenize(data.document);
    }

    @Benchmark
    public int luceneTokenStream(SampleData data) throws IOException {
        int totalTermLength = 0;
        try (TokenStream stream = data.luceneAnalyzer.tokenStream("body", data.text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                totalTermLength += term.length();
            }
            stream.end();
        }
        return totalTermLength;
    }

    @State(Scope.Thread)
    public static class SampleData {
        static final String JSON = """
                [
                  {"analysis":[{"lex":"мама","gr":"S,жен,од=им,мн"}],"text":"Мамы"},
                  {"analysis":[{"lex":"любить","gr":"V"}],"text":"любят"},
                  {"analysis":[],"text":"C"},
                  {"analysis":[{"lex":"писать","gr":"V"}],"text":"пишут"},
                  {"analysis":[],"text":"на"},
                  {"analysis":[],"text":"email"},
                  {"analysis":[],"text":"example"},
                  {"analysis":[],"text":"com"}
                ]
                """;

        final MystemJsonParser parser = new MystemJsonParser();
        final MystemSearchTokenizer tokenizer = new MystemSearchTokenizer();
        final MystemLuceneAnalyzer luceneAnalyzer = new MystemLuceneAnalyzer(new StaticMystemClient());
        final String text = "Мамы любят C++ и пишут на email@example.com";
        final String unicodeText = "Мамы\u0000 любят C++\u00AD и пишут на email@example.com\n";
        final String json = JSON;
        MystemDocument document;

        @Setup
        public void setUp() {
            document = parser.parse(text, json);
        }

        @TearDown
        public void tearDown() {
            luceneAnalyzer.close();
        }
    }

    private static final class StaticMystemClient implements MystemClient {
        @Override
        public MystemRawResult analyze(String text) {
            return new MystemRawResult(
                    text, SampleData.JSON, MystemOutputFormat.JSON, stats(text, SampleData.JSON));
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

    private static MystemRequestStats stats(String input, String output) {
        return new MystemRequestStats(
                Duration.ZERO,
                MystemExecutionMode.ONE_SHOT_TEXT,
                input.length(),
                input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                output.length(),
                output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
}
