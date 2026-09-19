package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

public class MystemLuceneOffsetCompositionTest extends BaseTokenStreamTestCase {
    private static final String ORIGINAL = "^😀&amp;О\u00ADдин|Один\r\n\uDBFF\uDFFF𐐀|хвост";
    private static final List<String> SURFACES = List.of("О\u00ADдин", "Один", "𐐀", "хвост");
    private static final List<String> TERMS = List.of("один", "один", "𐐨", "хвост");

    public void testComposesFiltersPreparationAlignmentAndChunkOffsets() throws IOException {
        for (int readSize : new int[] {1, 2, 5, 4096}) {
            for (int chunkSize : new int[] {7, 8, 13}) {
                for (boolean truncate : new boolean[] {false, true}) {
                    try (Analyzer analyzer = analyzer(readSize, chunkSize, truncate);
                            TokenStream stream = analyzer.tokenStream("body", ORIGINAL)) {
                        CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
                        OffsetAttribute offsets = stream.addAttribute(OffsetAttribute.class);
                        stream.reset();
                        int count = truncate ? 2 : SURFACES.size();
                        for (int index = 0; index < count; index++) {
                            assertTrue(stream.incrementToken());
                            assertEquals(TERMS.get(index), term.toString());
                            assertSourceRange(ORIGINAL, SURFACES.get(index), offsets.startOffset(), offsets.endOffset());
                        }
                        assertFalse(stream.incrementToken());
                        stream.end();
                        assertEquals(ORIGINAL.length(), offsets.startOffset());
                        assertEquals(ORIGINAL.length(), offsets.endOffset());
                    }
                }
            }
        }
    }

    public void testIndexedOffsetsSelectBothOriginalOccurrences() throws IOException {
        FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.freeze();
        try (Analyzer analyzer = analyzer(1, 8, false);
                Directory directory = newDirectory()) {
            try (IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(analyzer))) {
                Document document = new Document();
                document.add(new Field("body", ORIGINAL, type));
                writer.addDocument(document);
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                PostingsEnum postings = MultiTerms.getTermPostingsEnum(
                        reader, "body", new BytesRef("один"), PostingsEnum.ALL);
                assertNotNull(postings);
                assertEquals(0, postings.nextDoc());
                assertEquals(2, postings.freq());
                for (int index = 0; index < 2; index++) {
                    assertEquals(index, postings.nextPosition());
                    assertSourceRange(ORIGINAL, SURFACES.get(index), postings.startOffset(), postings.endOffset());
                }
                assertEquals(PostingsEnum.NO_MORE_DOCS, postings.nextDoc());
            }
        }
    }

    private static void assertSourceRange(String original, String surface, int start, int end) {
        int expectedStart = original.indexOf(surface);
        assertEquals(expectedStart, start);
        assertEquals(expectedStart + surface.length(), end);
        assertEquals(surface, original.substring(start, end));
    }

    private static Analyzer analyzer(int readSize, int chunkSize, boolean truncate) {
        NormalizeCharMap.Builder first = new NormalizeCharMap.Builder();
        first.add("^", "");
        first.add("&amp;", "&");
        first.add("|", "  ");
        NormalizeCharMap firstMap = first.build();
        NormalizeCharMap.Builder second = new NormalizeCharMap.Builder();
        second.add("&", " ");
        NormalizeCharMap secondMap = second.build();
        MystemLuceneAnalysisOptions options = MystemLuceneAnalysisOptions.builder()
                // The filtered prefix ends inside the supplementary letter when truncated.
                .maxInputChars(truncate ? 19 : 100)
                .maxChunkChars(chunkSize)
                .oversizedInputPolicy(MystemLuceneOversizedInputPolicy.TRUNCATE_AT_CODE_POINT_BOUNDARY)
                .build();
        return new Analyzer() {
            @Override
            protected Reader initReader(String fieldName, Reader reader) {
                return new MappingCharFilter(secondMap, new MappingCharFilter(firstMap, reader)) {
                    @Override
                    public int read(char[] buffer, int offset, int length) throws IOException {
                        return super.read(buffer, offset, Math.min(readSize, length));
                    }
                };
            }

            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                FakeMystemClient omissions = FakeMystemClient.mystemLikeOmissions();
                FakeMystemClient client = new FakeMystemClient(input -> omissions.analyze(input).output().replace(
                        "{\"analysis\":[],\"text\":\"Один\"}",
                        "{\"analysis\":[{\"lex\":\"один\",\"gr\":\"S\"}],\"text\":\"Один\"}"));
                return new TokenStreamComponents(new MystemLuceneTokenizer(
                        client, MystemSearchTokenizerOptions.conservative(), options));
            }
        };
    }
}
