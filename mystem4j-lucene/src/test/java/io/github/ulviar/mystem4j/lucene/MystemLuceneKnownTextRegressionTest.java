package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.TypeTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

/** Replays captured MyStem 3.1 output; known-text/provenance.json records the exact inputs and capture command. */
public class MystemLuceneKnownTextRegressionTest extends BaseTokenStreamTestCase {
    public void testCapturedNativeOutputPreservesFormsAndTokenAttributes() throws IOException {
        for (Fixture fixture : fixtures()) {
            for (boolean copied : List.of(false, true)) {
                List<Emission> expected = emissions(fixture);
                try (Analyzer analyzer = analyzer(fixture, copied)) {
                    // Captured output belongs to this exact input, so do not mutate it with randomized CharFilters.
                    try (TokenStream stream = analyzer.tokenStream("body", fixture.input())) {
                        assertTokenStreamContents(
                                stream,
                                expected.stream().map(Emission::term).toArray(String[]::new),
                                expected.stream().mapToInt(Emission::start).toArray(),
                                expected.stream().mapToInt(Emission::end).toArray(),
                                expected.stream().map(Emission::type).toArray(String[]::new),
                                expected.stream().mapToInt(Emission::increment).toArray(),
                                fixture.input().length());
                    }
                    try (TokenStream stream = analyzer.tokenStream("body", fixture.input())) {
                        KeywordAttribute keyword = stream.addAttribute(KeywordAttribute.class);
                        stream.reset();
                        for (Emission emission : expected) {
                            assertTrue(fixture.name(), stream.incrementToken());
                            assertEquals(fixture.name() + ": " + emission.term(), emission.keyword(), keyword.isKeyword());
                        }
                        assertFalse(stream.incrementToken());
                        stream.end();
                    }
                }
            }
        }
    }

    public void testKnownFormsRemainSearchableAtTheOriginalPositionsAndOffsets() throws IOException {
        for (Fixture fixture : fixtures()) {
            for (boolean copied : List.of(false, true)) {
                try (Analyzer analyzer = analyzer(fixture, copied);
                        Directory directory = newDirectory()) {
                    index(directory, analyzer, fixture.input());
                    try (DirectoryReader reader = DirectoryReader.open(directory)) {
                        IndexSearcher searcher = newSearcher(reader);
                        Map<String, List<Emission>> terms = new LinkedHashMap<>();
                        for (Emission emission : emissions(fixture)) {
                            terms.computeIfAbsent(emission.term(), ignored -> new ArrayList<>()).add(emission);
                        }
                        for (Map.Entry<String, List<Emission>> term : terms.entrySet()) {
                            assertEquals(fixture.name() + ": " + term.getKey(), 1,
                                    searcher.count(new TermQuery(new Term("body", term.getKey()))));
                            PostingsEnum postings = MultiTerms.getTermPostingsEnum(
                                    reader, "body", new BytesRef(term.getKey()), PostingsEnum.ALL);
                            assertNotNull(postings);
                            assertEquals(0, postings.nextDoc());
                            assertEquals(term.getValue().size(), postings.freq());
                            for (Emission occurrence : term.getValue()) {
                                assertEquals(occurrence.position(), postings.nextPosition());
                                assertEquals(occurrence.start(), postings.startOffset());
                                assertEquals(occurrence.end(), postings.endOffset());
                            }
                            assertEquals(PostingsEnum.NO_MORE_DOCS, postings.nextDoc());
                        }
                        if (fixture.name().equals("soft-hyphen-lemmas")) {
                            assertEquals(1, searcher.count(new PhraseQuery("body", "кот", "собака", "мышь")));
                            assertEquals(0, searcher.count(new PhraseQuery("body", "кот", "мышь", "собака")));
                        }
                    }
                }
            }
        }
    }

    public void testLowercaseCurrencyCodeAndTypeWorkInAnIndexAndQuery() throws IOException {
        Fixture currency = fixtures().stream().filter(fixture -> fixture.name().equals("currency")).findFirst().orElseThrow();
        for (boolean copied : List.of(false, true)) {
            FakeMystemClient client = client(currency, copied);
            try (Analyzer currencyOnly = new Analyzer() {
                        @Override
                        protected TokenStreamComponents createComponents(String fieldName) {
                            MystemLuceneTokenizer tokenizer =
                                    new MystemLuceneTokenizer(client, MystemSearchTokenizerOptions.entityAware());
                            return new TokenStreamComponents(
                                    tokenizer, new TypeTokenFilter(tokenizer, Set.of("currency"), true));
                        }
                    };
                    Analyzer queryAnalyzer = new MystemLuceneAnalyzer(new FakeMystemClient(input -> {
                        assertEquals("USD", input);
                        return raw("currency-query", copied);
                    }));
                    Directory directory = newDirectory()) {
                index(directory, currencyOnly, currency.input());
                try (DirectoryReader reader = DirectoryReader.open(directory);
                        TokenStream query = queryAnalyzer.tokenStream("body", "USD")) {
                    CharTermAttribute term = query.addAttribute(CharTermAttribute.class);
                    query.reset();
                    assertTrue(query.incrementToken());
                    assertEquals("usd", term.toString());
                    assertEquals(1, newSearcher(reader).count(new TermQuery(new Term("body", term.toString()))));
                    assertFalse(query.incrementToken());
                    query.end();
                }
            }
        }
    }

    private void index(Directory directory, Analyzer analyzer, String input) throws IOException {
        FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.freeze();
        try (IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(analyzer))) {
            Document document = new Document();
            document.add(new Field("body", input, type));
            writer.addDocument(document);
        }
    }

    private static Analyzer analyzer(Fixture fixture, boolean copied) {
        return new MystemLuceneAnalyzer(client(fixture, copied), MystemSearchTokenizerOptions.entityAware());
    }

    private static FakeMystemClient client(Fixture fixture, boolean copied) {
        String output = raw(fixture.name(), copied);
        return new FakeMystemClient(input -> {
            assertEquals(fixture.input(), input);
            return output;
        });
    }

    private static String raw(String name, boolean copied) {
        String path = "/known-text/" + name + (copied ? "-copied.json" : "-omitted.json");
        try (InputStream input = MystemLuceneKnownTextRegressionTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing native fixture " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<Emission> emissions(Fixture fixture) {
        List<Emission> result = new ArrayList<>();
        int cursor = 0;
        int position = 0;
        for (Group group : fixture.groups()) {
            int start = fixture.input().indexOf(group.surface(), cursor);
            assertTrue("Missing source surface in " + fixture.name(), start >= cursor);
            int end = start + group.surface().length();
            for (int index = 0; index < group.forms().size(); index++) {
                result.add(new Emission(group.forms().get(index), start, end, group.type(), group.keyword(),
                        index == 0 ? 1 : 0, position));
            }
            cursor = end;
            position++;
        }
        return result;
    }

    private static List<Fixture> fixtures() {
        return List.of(
                new Fixture("soft-hyphen-lemmas", "😀 Коты\u00AD Собаки\u00AD Мыши", List.of(
                        group("Коты\u00AD", "word", true, "кот"),
                        group("Собаки\u00AD", "word", true, "собака"),
                        group("Мыши", "word", true, "мышь"))),
                new Fixture("soft-hyphen-fallback", "😀 Fo\u00ADur Fo\u00ADur+ Fo\u00ADur++ Fo\u00ADur# 11\u00AD22+ 11\u00AD22++ 11\u00AD22#", List.of(
                        group("Fo\u00ADur", "word", false, "four"),
                        group("Fo\u00ADur+", "word", false, "four+", "four"),
                        group("Fo\u00ADur++", "word", false, "four++", "four"),
                        group("Fo\u00ADur#", "word", false, "four#", "four"),
                        group("11\u00AD22+", "number", true, "1122+", "1122"),
                        group("11\u00AD22++", "number", true, "1122++", "1122"),
                        group("11\u00AD22#", "number", true, "1122#", "1122"))),
                new Fixture("accent-suffix", "😀 Foo\u0301++ 111\u0301++", List.of(
                        group("Foo\u0301++", "word", false, "foo++", "foo"),
                        group("111\u0301++", "number", true, "111++", "111"))),
                new Fixture("idn-url", "😀 https://пример.рф", List.of(
                        group("https://пример.рф", "url", true, "https://пример.рф", "пример.рф"))),
                new Fixture("idn-www-url", "😀 https://www.пример.рф/path", List.of(
                        group("https://www.пример.рф/path", "url", true, "https://www.пример.рф/path", "пример.рф"))),
                new Fixture("www-dot-url", "😀 https://www.example.com./path", List.of(
                        group("https://www.example.com./path", "url", true, "https://www.example.com./path", "example.com"))),
                new Fixture("email-local-punctuation", "😀 o'reilly@example.com,a!b@example.com", List.of(
                        group("o'reilly@example.com", "email", true, "o'reilly@example.com", "example.com"),
                        group("a!b@example.com", "email", true, "a!b@example.com", "example.com"))),
                new Fixture("ordinary-lemmas", "😀 Привет, мир!", List.of(
                        group("Привет", "word", true, "привет"),
                        group("мир", "word", true, "мир"))),
                new Fixture("final-synonyms", "😀 C++", List.of(group("C++", "word", false, "c++", "c"))),
                new Fixture("currency", "😀 $", List.of(
                        group("$", "currency", true, "$", "доллар", "dollar", "dólar", "美元", "usd"))));
    }

    private static Group group(String surface, String type, boolean keyword, String... forms) {
        return new Group(surface, type, keyword, List.of(forms));
    }

    private record Fixture(String name, String input, List<Group> groups) {}

    private record Group(String surface, String type, boolean keyword, List<String> forms) {}

    private record Emission(String term, int start, int end, String type, boolean keyword, int increment, int position) {}
}
