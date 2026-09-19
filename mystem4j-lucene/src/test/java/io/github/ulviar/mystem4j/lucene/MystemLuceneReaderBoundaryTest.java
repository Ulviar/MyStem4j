package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class MystemLuceneReaderBoundaryTest extends BaseTokenStreamTestCase {
    public void testSupplementaryLetterSurvivesSingleCharacterReads() throws IOException {
        Result result = analyze("𐐀B", 1, 1, 100);

        assertEquals(List.of("𐐀", "B"), result.requests());
        assertEquals(List.of(new Emission("𐐨", 0, 2, 1), new Emission("b", 2, 3, 1)), result.emissions());
        assertEquals(3, result.finalOffset());
    }

    public void testTruncationRemovesPairSplitAcrossReads() throws IOException {
        Result result = analyze("A𐐀B", 2, 2, 2);

        assertEquals(List.of("A"), result.requests());
        assertEquals(List.of(new Emission("a", 0, 1, 1)), result.emissions());
        assertEquals(4, result.finalOffset());
    }

    public void testTruncatedPrefixStillUsesBoundedChunks() throws IOException {
        Result result = analyze("ABCDEFGHI", 4096, 3, 8);

        assertEquals(List.of("ABC", "DEF", "GH"), result.requests());
        assertEquals(List.of(
                new Emission("abc", 0, 3, 1),
                new Emission("def", 3, 6, 1),
                new Emission("gh", 6, 8, 1)), result.emissions());
        assertEquals(9, result.finalOffset());
    }

    public void testOutputIsIndependentOfReaderFragmentation() throws IOException {
        // Includes contraction, dropped characters, CR/LF, combining marks and an unpaired surrogate.
        String original = "A𐐀B О\u00ADдин Один\r\n\uDBFF\uDFFFé e\u0301\uD800 Z";
        for (int chunkSize = 1; chunkSize <= 9; chunkSize++) {
            for (int inputLimit = 1; inputLimit <= original.length() + 1; inputLimit++) {
                Result expected = analyze(original, 4096, chunkSize, inputLimit);
                for (int readSize = 1; readSize <= 7; readSize++) {
                    assertEquals("chunk=" + chunkSize + ", limit=" + inputLimit + ", read=" + readSize,
                            expected, analyze(original, readSize, chunkSize, inputLimit));
                }
            }
        }
    }

    private static Result analyze(String original, int readSize, int chunkSize, int inputLimit) throws IOException {
        FakeMystemClient client = FakeMystemClient.mystemLikeOmissions();
        MystemLuceneAnalysisOptions options = MystemLuceneAnalysisOptions.builder()
                .maxInputChars(inputLimit)
                .maxChunkChars(Math.min(chunkSize, inputLimit))
                .oversizedInputPolicy(MystemLuceneOversizedInputPolicy.TRUNCATE_AT_CODE_POINT_BOUNDARY)
                .build();
        try (MystemLuceneTokenizer tokenizer = new MystemLuceneTokenizer(
                client, MystemSearchTokenizerOptions.conservative(), options)) {
            tokenizer.setReader(fragmentedReader(original, readSize));
            CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsets = tokenizer.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positions = tokenizer.addAttribute(PositionIncrementAttribute.class);
            List<Emission> emissions = new ArrayList<>();
            tokenizer.reset();
            while (tokenizer.incrementToken()) {
                emissions.add(new Emission(term.toString(), offsets.startOffset(), offsets.endOffset(),
                        positions.getPositionIncrement()));
            }
            tokenizer.end();
            assertEquals(original.length(), offsets.endOffset());
            return new Result(client.requests(), List.copyOf(emissions), offsets.endOffset());
        }
    }

    private static Reader fragmentedReader(String original, int readSize) {
        return new StringReader(original) {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                return super.read(buffer, offset, Math.min(length, readSize));
            }
        };
    }

    private record Emission(String term, int start, int end, int positionIncrement) {}

    private record Result(List<String> requests, List<Emission> emissions, int finalOffset) {}
}
