package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.UnicodeUtil;

public class MystemLuceneTermLimitTest extends BaseTokenStreamTestCase {
    public void testIndexesOnlyFormsWithinTheUtf8ByteLimit() throws IOException {
        for (String character : new String[] {"a", "я", "漢", "𐐨"}) {
            int bytes = UnicodeUtil.calcUTF16toUTF8Length(character, 0, character.length());
            for (int delta : new int[] {-1, 0, 1}) {
                int length = IndexWriter.MAX_TERM_LENGTH + delta;
                String text = character.repeat(length / bytes) + "a".repeat(length % bytes);
                try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo())) {
                    String[] expected = delta <= 0 ? new String[] {text} : new String[0];
                    assertAnalyzesTo(analyzer, text, expected);
                    assertIndexed(analyzer, text, delta <= 0 ? text : "a", delta <= 0 ? 1 : 0);
                }
            }
        }
    }

    public void testDefaultChunkLimitsDoNotPermitImmenseTerms() throws IOException {
        for (String text : new String[] {"a".repeat(32768), "я".repeat(16384), "漢".repeat(10923)}) {
            try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo())) {
                assertAnalyzesTo(analyzer, text, new String[0]);
                assertIndexed(analyzer, text, "a", 0);
            }
        }
    }

    public void testKeepsSafeAliasesWhenLowercasingExpandsPastTheLimit() throws IOException {
        String text = "İ".repeat(10923);
        String alias = "i".repeat(10923);
        try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo())) {
            assertAnalyzesTo(analyzer, text, new String[] {alias}, new int[] {0},
                    new int[] {text.length()}, new String[] {"word"}, new int[] {1});
            assertIndexed(analyzer, text, alias, 1);
        }
    }

    public void testKeepsSafeLemmaAlternativesAndTheOriginalPosition() throws IOException {
        String immense = "漢".repeat(10923);
        FakeMystemClient client = new FakeMystemClient(input ->
                "[{\"text\":\"A\",\"analysis\":[{\"lex\":" + FakeMystemClient.jsonString(immense)
                        + "},{\"lex\":\"safe\"},{\"lex\":\"alias\"}]}]");
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            assertAnalyzesTo(analyzer, "A", new String[] {"safe", "alias"}, new int[] {0, 0},
                    new int[] {1, 1}, new String[] {"word", "word"}, new int[] {1, 0});
            assertIndexed(analyzer, "A", "safe", 1);
        }
    }

    public void testOversizedUrlRetainsItsSafeDomainAlternative() throws IOException {
        String text = "https://example.com/" + "a".repeat(40000);
        MystemLuceneAnalysisOptions options = MystemLuceneAnalysisOptions.builder().maxChunkChars(100000).build();
        try (Analyzer analyzer = new MystemLuceneAnalyzer(new FakeMystemClient(input -> "[]"),
                MystemSearchTokenizerOptions.entityAware(), options)) {
            assertAnalyzesTo(analyzer, text, new String[] {"example.com"}, new int[] {0},
                    new int[] {text.length()}, new String[] {"url"}, new int[] {1});
            assertIndexed(analyzer, text, "example.com", 1);
        }
    }

    public void testLemmaByteLimitUsesLucenesReplacementForUnpairedSurrogates() throws IOException {
        String lemma = "\uD800".repeat(10923);
        FakeMystemClient client = new FakeMystemClient(input ->
                "[{\"text\":\"A\",\"analysis\":[{\"lex\":" + FakeMystemClient.jsonString(lemma) + "}]}]");
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
            assertAnalyzesTo(analyzer, "A", new String[0]);
            assertIndexed(analyzer, "A", "a", 0);
        }
    }

    public void testSkippedWholeTokensKeepPositionsAcrossChunksAndInEndState() throws IOException {
        String huge = "я".repeat(16384);
        for (MystemLucenePositionPolicy policy : MystemLucenePositionPolicy.values()) {
            for (int chunk : new int[] {32768, 100000}) {
                MystemLuceneAnalysisOptions options = MystemLuceneAnalysisOptions.builder()
                        .maxChunkChars(chunk).positionPolicy(policy).build();
                try (Analyzer analyzer = new MystemLuceneAnalyzer(new FakeMystemClient(input -> "[]"),
                        MystemSearchTokenizerOptions.conservative(), options)) {
                    String text = "before " + huge + " after " + huge;
                    int after = text.indexOf("after");
                    int increment = policy == MystemLucenePositionPolicy.COMPACT ? 2 : 4;
                    int trailing = policy == MystemLucenePositionPolicy.COMPACT ? 1 : 2;
                    assertAnalyzesTo(analyzer, text, new String[] {"before", "after"}, new int[] {0, after},
                            new int[] {6, after + 5}, new String[] {"word", "word"}, new int[] {1, increment});
                    try (TokenStream stream = analyzer.tokenStream("body", text)) {
                        PositionIncrementAttribute positions = stream.addAttribute(PositionIncrementAttribute.class);
                        OffsetAttribute offsets = stream.addAttribute(OffsetAttribute.class);
                        stream.reset();
                        while (stream.incrementToken()) {}
                        stream.end();
                        assertEquals(trailing, positions.getPositionIncrement());
                        assertEquals(text.length(), offsets.endOffset());
                    }
                    try (Directory directory = newDirectory()) {
                        try (IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(analyzer))) {
                            Document document = new Document();
                            document.add(new TextField("body", text, Field.Store.NO));
                            writer.addDocument(document);
                        }
                        try (DirectoryReader reader = DirectoryReader.open(directory)) {
                            IndexSearcher searcher = newSearcher(reader);
                            assertEquals(0, searcher.count(new PhraseQuery("body", "before", "after")));
                            assertEquals(1, searcher.count(new PhraseQuery.Builder()
                                    .add(new Term("body", "before"), 0)
                                    .add(new Term("body", "after"), increment).build()));
                        }
                    }
                    assertAnalyzesTo(analyzer, "reuse", new String[] {"reuse"});
                }
            }
        }
    }

    public void testDirectTokenizerSkipsOversizedFormsAndNormalizeRetainsOneTerm() throws IOException {
        String text = "a".repeat(40000);
        MystemLuceneAnalysisOptions options = MystemLuceneAnalysisOptions.builder().maxChunkChars(100000).build();
        try (MystemLuceneTokenizer tokenizer = new MystemLuceneTokenizer(FakeMystemClient.echo(),
                MystemSearchTokenizerOptions.conservative(), options)) {
            tokenizer.setReader(new StringReader(text));
            PositionIncrementAttribute position = tokenizer.addAttribute(PositionIncrementAttribute.class);
            OffsetAttribute offset = tokenizer.addAttribute(OffsetAttribute.class);
            tokenizer.reset();
            assertFalse(tokenizer.incrementToken());
            tokenizer.end();
            assertEquals(1, position.getPositionIncrement());
            assertEquals(text.length(), offset.endOffset());
        }
        try (Analyzer analyzer = new MystemLuceneAnalyzer(FakeMystemClient.echo())) {
            assertEquals(text, analyzer.normalize("body", text).utf8ToString());
        }
    }

    private static void assertIndexed(Analyzer analyzer, String input, String term, int hits) throws IOException {
        try (Directory directory = newDirectory()) {
            // Use the production codec for maximum-size terms. Randomized FST codecs
            // can spend minutes in CheckIndex on one term at Lucene's byte limit.
            try (IndexWriter writer = new IndexWriter(directory,
                    newIndexWriterConfig(analyzer).setCodec(TestUtil.getDefaultCodec()))) {
                Document document = new Document();
                document.add(new TextField("body", input, Field.Store.NO));
                writer.addDocument(document);
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                assertEquals(hits, newSearcher(reader).count(new TermQuery(new Term("body", term))));
            }
        }
    }
}
