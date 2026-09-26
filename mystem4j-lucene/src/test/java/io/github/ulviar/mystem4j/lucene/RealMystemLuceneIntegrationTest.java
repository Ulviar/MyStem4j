package io.github.ulviar.mystem4j.lucene;

import static org.junit.Assume.assumeFalse;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

@ThreadLeakFilters(defaultFilters = true, filters = ProcwrightSharedThreadsFilter.class)
public class RealMystemLuceneIntegrationTest extends LuceneTestCase {
    private static final String FIELD = "body";

    public void testOriginalOffsetsThroughPreparationChunkingAndTruncation() throws IOException {
        String executable = System.getProperty("mystem4j.executable", "");
        assumeFalse("Set -Dmystem4j.executable=/path/to/mystem to run real MyStem Lucene tests.", executable.isBlank());
        String original = "😀\uDBFF\uDFFFО\u00ADдин Один\r\nМамы хвост";
        String[] terms = {"один", "один", "мама", "хвост"};
        int[] starts = {4, 10, 16, 21};
        int[] ends = {9, 14, 20, 26};
        try (MystemClient client = Mystem.builder()
                .executable(Path.of(executable))
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .disambiguate(true)
                        .build())
                .pooled()
                .build()) {
            for (int readSize : new int[] {1, 2, 4096}) {
                for (int chunkSize : new int[] {10, 11, 17}) {
                    for (boolean truncate : new boolean[] {false, true}) {
                        MystemLuceneAnalysisOptions options = MystemLuceneAnalysisOptions.builder()
                                .maxInputChars(truncate ? 20 : 100)
                                .maxChunkChars(chunkSize)
                                .oversizedInputPolicy(MystemLuceneOversizedInputPolicy.TRUNCATE_AT_CODE_POINT_BOUNDARY)
                                .build();
                        try (Analyzer analyzer = new MystemLuceneAnalyzer(
                                client, MystemSearchTokenizerOptions.conservative(), options);
                                TokenStream stream = analyzer.tokenStream(FIELD, new StringReader(original) {
                                    @Override
                                    public int read(char[] buffer, int offset, int length) throws IOException {
                                        return super.read(buffer, offset, Math.min(length, readSize));
                                    }
                                })) {
                            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
                            OffsetAttribute offsets = stream.addAttribute(OffsetAttribute.class);
                            stream.reset();
                            int count = truncate ? 3 : 4;
                            for (int index = 0; index < count; index++) {
                                assertTrue(stream.incrementToken());
                                assertEquals(terms[index], term.toString());
                                assertEquals(starts[index], offsets.startOffset());
                                assertEquals(ends[index], offsets.endOffset());
                            }
                            assertFalse(stream.incrementToken());
                            stream.end();
                            assertEquals(original.length(), offsets.endOffset());
                        }
                    }
                }
            }
        }
    }

    public void testPooledClientIndexesMultilineFields() throws IOException {
        String executable = System.getProperty("mystem4j.executable", "");
        assumeFalse("Set -Dmystem4j.executable=/path/to/mystem to run real MyStem Lucene tests.", executable.isBlank());

        try (MystemClient client = Mystem.builder()
                        .executable(Path.of(executable))
                        .options(MystemOptions.builder()
                                .format(MystemOutputFormat.JSON)
                                .grammarInfo(true)
                                .disambiguate(true)
                                .build())
                        .pooled()
                        .build();
                Analyzer analyzer = new MystemLuceneAnalyzer(client);
                Directory directory = indexOne(analyzer, "Мамы\nПапы")) {
            assertHitCount(directory, "мама", 1);
            assertHitCount(directory, "папа", 1);
        }
    }

    private static Directory indexOne(Analyzer analyzer, String text) throws IOException {
        Directory directory = newDirectory();
        try (IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(analyzer))) {
            Document document = new Document();
            document.add(new TextField(FIELD, text, Field.Store.NO));
            writer.addDocument(document);
        }
        return directory;
    }

    private static void assertHitCount(Directory directory, String term, int expected) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = newSearcher(reader);
            assertEquals(expected, searcher.count(new TermQuery(new Term(FIELD, term))));
        }
    }
}
