package io.github.ulviar.mystem4j.benchmark;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/** Opt-in measurements including the external process, parsing and indexing. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
@Threads(4)
public class MystemNativeBenchmark {
    @Benchmark
    public MystemRawResult nativeRequest(NativeData data, Cursor cursor) {
        return data.client.analyze(data.preparedTexts.get(cursor.next(data.preparedTexts.size())));
    }

    @Benchmark
    public int luceneAnalysis(NativeData data, Cursor cursor) throws IOException {
        int termLength = 0;
        try (TokenStream stream = data.analyzer.tokenStream("body", data.texts.get(cursor.next(data.texts.size())))) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                termLength += term.length();
            }
            stream.end();
        }
        return termLength;
    }

    @Benchmark
    public int indexBatch(NativeData data) throws IOException {
        try (Directory directory = data.indexCorpus()) {
            return directory.listAll().length;
        }
    }

    @State(Scope.Thread)
    public static class Cursor {
        private int index;

        int next(int size) {
            int current = index;
            index = (index + 1) % size;
            return current;
        }
    }

    @State(Scope.Benchmark)
    public static class NativeData {
        @Param({""})
        public String executable;
        @Param({"1024", "16384", "131072"})
        public int inputChars;
        @Param({"1", "4"})
        public int poolSize;
        @Param({"4"})
        public int documentsPerBatch;
        @Param({""})
        public String corpusDirectory = "";
        MystemClient client;
        MystemLuceneAnalyzer analyzer;
        List<String> texts;
        List<String> preparedTexts;

        @Setup
        public void setUp() throws IOException {
            if (executable == null || executable.isBlank()) {
                throw new IllegalArgumentException("Use nativeJmh with -Dmystem4j.executable=/path/to/mystem");
            }
            texts = BenchmarkCorpus.documents(inputChars, corpusDirectory);
            preparedTexts = texts.stream().map(text ->
                    io.github.ulviar.mystem4j.model.MystemTextPreprocessor.prepareJsonLine(text).text()).toList();
            client = Mystem.builder().executable(Path.of(executable))
                    .options(MystemOptions.builder().grammarInfo(true).disambiguate(true).build())
                    .requestTimeout(Duration.ofSeconds(30))
                    .pooled(pool -> pool.maxSize(poolSize).warmupSize(poolSize).minIdle(poolSize)
                            .acquireTimeout(Duration.ofSeconds(30)))
                    .build();
            analyzer = new MystemLuceneAnalyzer(client);
            // Validate the actual native path before JMH starts the timed workload.
            try (Directory directory = indexCorpus(); DirectoryReader reader = DirectoryReader.open(directory)) {
                var terms = MultiTerms.getTerms(reader, "body");
                var firstTerm = terms == null ? null : terms.iterator().next();
                if (reader.numDocs() != documentsPerBatch || firstTerm == null
                        || new IndexSearcher(reader).count(new TermQuery(new Term("body", firstTerm))) < 1) {
                    throw new IllegalStateException("Native benchmark did not create a searchable index");
                }
            } catch (IOException | RuntimeException failure) {
                tearDown();
                throw failure;
            }
        }

        Directory indexCorpus() throws IOException {
            Directory directory = new ByteBuffersDirectory();
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                for (int index = 0; index < documentsPerBatch; index++) {
                    Document document = new Document();
                    document.add(new TextField("body", texts.get(index % texts.size()), Field.Store.NO));
                    writer.addDocument(document);
                }
            } catch (IOException | RuntimeException failure) {
                directory.close();
                throw failure;
            }
            return directory;
        }

        @TearDown
        public void tearDown() {
            try {
                if (analyzer != null) {
                    analyzer.close();
                }
            } finally {
                if (client != null) {
                    client.close();
                }
            }
        }
    }
}
