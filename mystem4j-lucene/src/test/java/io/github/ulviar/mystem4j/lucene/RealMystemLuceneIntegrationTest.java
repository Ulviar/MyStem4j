package io.github.ulviar.mystem4j.lucene;

import static org.junit.Assume.assumeFalse;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
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

public class RealMystemLuceneIntegrationTest extends LuceneTestCase {
    private static final String FIELD = "body";

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
