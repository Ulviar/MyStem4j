package io.github.ulviar.mystem4j.lucene;

import static org.apache.lucene.tests.analysis.BaseTokenStreamTestCase.assertTokenStreamContents;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class MystemLuceneNormalizationTest extends LuceneTestCase {
    public void testAliasesKeepTheirOriginalUtf16RangesAndSynonymPositions() throws IOException {
        try (Analyzer analyzer = new MystemLuceneAnalyzer(new FakeMystemClient(input -> "[]"));
                TokenStream stream = analyzer.tokenStream("body", "😀 İstanbul ΟΣ")) {
            assertTokenStreamContents(stream, new String[]{"i\u0307stanbul", "istanbul", "ος", "οσ"},
                    new int[]{3, 3, 12, 12}, new int[]{11, 11, 14, 14},
                    new int[]{1, 0, 1, 0}, 14);
        }
    }

    public void testNormalizesOneTermWithoutMorphologyOrNativeRequests() {
        FakeMystemClient client = new FakeMystemClient(input -> {
            throw new AssertionError("normalize must not invoke MyStem");
        });
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            for (String[] testCase : List.of(
                    new String[]{"Fo\u00ADur*", "four*"},
                    new String[]{"Foo\u0301?", "foo?"},
                    new String[]{"Foo\u0341++", "foo++"},
                    new String[]{"İstanbul", "istanbul"},
                    new String[]{"ΟΣ", "οσ"},
                    new String[]{"\uD801\uDC00O\u0308", "\uD801\uDC28o\u0308"},
                    new String[]{"C++", "c++"},
                    new String[]{"Мамы", "мамы"},
                    new String[]{"", ""},
                    new String[]{"\u00AD\u0301\u0341", ""})) {
                assertEquals(testCase[1], analyzer.normalize("body", testCase[0]).utf8ToString());
            }
        }
        assertTrue(client.requests().isEmpty());
    }

    public void testNormalizedPrefixAndWildcardQueriesFindIndexedForms() throws IOException {
        for (String[] testCase : List.of(
                new String[]{"Fo\u00ADur", "Fo\u00AD"},
                new String[]{"Foo\u0301", "Fo"},
                new String[]{"Foo\u0341", "Foo\u0341"},
                new String[]{"İstanbul", "İst"},
                new String[]{"ΟΣ", "ΟΣ"},
                new String[]{"ΟΣΑ", "ΟΣ"},
                new String[]{"\uD801\uDC00OO", "\uD801\uDC00O"})) {
            try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo());
                    Directory directory = index(analyzer, testCase[0]);
                    DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = newSearcher(reader);
                assertEquals(testCase[0], 1, searcher.count(new TermQuery(
                        new Term("body", analyzer.normalize("body", testCase[0])))));
                assertEquals(testCase[0], 1, searcher.count(new PrefixQuery(
                        new Term("body", analyzer.normalize("body", testCase[1])))));
                assertEquals(testCase[0], 1, searcher.count(new WildcardQuery(
                        new Term("body", analyzer.normalize("body", testCase[1] + "*")))));
            }
        }
    }

    public void testEntityLiteralsRemainSearchableAlongsideNormalizedAliases() throws IOException {
        for (String source : List.of("https://example.com/Foo\u0301", "fo\u0301o@example.com",
                "https://bu\u0301cher.de/path", "İstanbul@example.com")) {
            try (Analyzer analyzer = new MystemLuceneAnalyzer(
                    new FakeMystemClient(input -> "[]"), MystemSearchTokenizerOptions.entityAware());
                    Directory directory = index(analyzer, source);
                    DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = newSearcher(reader);
                assertEquals(source, 1, searcher.count(new TermQuery(new Term("body", source.toLowerCase(Locale.ROOT)))));
                assertEquals(source, 1, searcher.count(new TermQuery(new Term("body", analyzer.normalize("body", source)))));
                assertEquals(source, 1, searcher.count(new PrefixQuery(new Term("body", analyzer.normalize("body", source)))));
            }
        }
    }

    private Directory index(Analyzer analyzer, String source) throws IOException {
        Directory directory = newDirectory();
        try (IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(analyzer))) {
            Document document = new Document();
            document.add(new TextField("body", source, Field.Store.NO));
            writer.addDocument(document);
        }
        return directory;
    }
}
