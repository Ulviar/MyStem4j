package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
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

public class MystemLuceneIndexRoundTripTest extends LuceneTestCase {
    private static final String FIELD = "body";

    public void testIndexesMystemLemmasForTermSearch() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"Мамы".equals(input)) {
                return "[]";
            }
            return """
                    [{"analysis":[{"lex":"мама","gr":"S"}],"text":"Мамы"}]
                    """;
        });

        try (Analyzer analyzer = new MystemLuceneAnalyzer(client);
                Directory directory = indexOne(analyzer, "Мамы")) {
            assertHitCount(directory, "мама", 1);
            assertHitCount(directory, "мамы", 0);
        }
    }

    public void testIndexesMultilineFieldsWithJsonLineCompatibleClient() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> switch (input) {
            case "Мамы" -> """
                    [{"analysis":[{"lex":"мама","gr":"S"}],"text":"Мамы"}]
                    """;
            case "Папы" -> """
                    [{"analysis":[{"lex":"папа","gr":"S"}],"text":"Папы"}]
                    """;
            default -> {
                if (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0) {
                    throw new AssertionError("client received multiline input: " + input);
                }
                yield "[]";
            }
        });

        try (Analyzer analyzer = new MystemLuceneAnalyzer(client);
                Directory directory = indexOne(analyzer, "Мамы\nПапы")) {
            assertHitCount(directory, "мама", 1);
            assertHitCount(directory, "папа", 1);
        }
    }

    public void testIndexesAlternativeSuffixFormsAtTheSameSourceToken() throws IOException {
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

        try (Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.entityAware());
                Directory directory = indexOne(analyzer, "Раз++ C++")) {
            assertHitCount(directory, "раз++", 1);
            assertHitCount(directory, "раз", 1);
            assertHitCount(directory, "c++", 1);
            assertHitCount(directory, "c", 1);
        }
    }

    public void testConservativeAndEntityAwarePoliciesIndexDifferentSearchTerms() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> {
            if (!"Visit https://example.com".equals(input)) {
                return "[]";
            }
            return """
                    [
                      {"analysis":[],"text":"Visit"},
                      {"analysis":[],"text":"https"},
                      {"analysis":[],"text":"example"},
                      {"analysis":[],"text":"com"}
                    ]
                    """;
        });

        try (Analyzer analyzer = new MystemLuceneAnalyzer(client);
                Directory directory = indexOne(analyzer, "Visit https://example.com")) {
            assertHitCount(directory, "https", 1);
            assertHitCount(directory, "example", 1);
            assertHitCount(directory, "com", 1);
            assertHitCount(directory, "https://example.com", 0);
            assertHitCount(directory, "example.com", 0);
        }

        try (Analyzer analyzer = new MystemLuceneAnalyzer(client, MystemSearchTokenizerOptions.entityAware());
                Directory directory = indexOne(analyzer, "Visit https://example.com")) {
            assertHitCount(directory, "https", 0);
            assertHitCount(directory, "example", 0);
            assertHitCount(directory, "com", 0);
            assertHitCount(directory, "https://example.com", 1);
            assertHitCount(directory, "example.com", 1);
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
