package io.github.ulviar.mystem4j.benchmark;

import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalysisOptions;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemTextPreprocessor;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
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
    public Object luceneDefaults() {
        return MystemLuceneAnalysisOptions.defaults();
    }

    @State(Scope.Thread)
    public static class SampleData {
        final MystemJsonParser parser = new MystemJsonParser();
        final MystemSearchTokenizer tokenizer = new MystemSearchTokenizer();
        final String text = "Мамы любят C++ и пишут на email@example.com";
        final String unicodeText = "Мамы\u0000 любят C++\u00AD и пишут на email@example.com\n";
        final String json = """
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
        MystemDocument document;

        @Setup
        public void setUp() {
            document = parser.parse(text, json);
        }
    }
}
