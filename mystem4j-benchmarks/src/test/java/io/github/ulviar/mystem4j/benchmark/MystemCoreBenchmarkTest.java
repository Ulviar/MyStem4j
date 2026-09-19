package io.github.ulviar.mystem4j.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemPreparedText;
import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MystemCoreBenchmarkTest {
    @Test
    void corpusContainsDifferentDocumentsAndSupportsExternalUtf8(@TempDir Path directory) throws IOException {
        List<String> builtin = BenchmarkCorpus.documents(1024, "");
        assertEquals(4, builtin.stream().distinct().count());
        assertThrows(IOException.class, () -> BenchmarkCorpus.documents(1024, directory.toString()));
        Files.writeString(directory.resolve("source.txt"), "Я😀документ");
        String shortened = BenchmarkCorpus.documents(2, directory.toString()).getFirst();
        assertEquals("Я ", shortened); // Truncation cannot leave half of the source surrogate pair.
        assertEquals("Я😀", BenchmarkCorpus.documents(3, directory.toString()).getFirst());
        Files.writeString(directory.resolve("empty.txt"), " ");
        assertThrows(IOException.class, () -> BenchmarkCorpus.documents(1024, directory.toString()));
    }

    @ParameterizedTest
    @CsvSource({"1024,0", "1024,1", "1024,2", "1024,3", "16384,0", "16384,1", "16384,2", "16384,3", "131072,0", "131072,1", "131072,2", "131072,3"})
    void benchmarkSampleDataProducesAlignedResults(int inputChars, int documentIndex) throws IOException {
        MystemCoreBenchmark benchmark = new MystemCoreBenchmark();
        MystemCoreBenchmark.SampleData data = new MystemCoreBenchmark.SampleData();
        data.inputChars = inputChars;
        data.documentIndex = documentIndex;
        data.setUp();
        try {
            MystemDocument document = assertInstanceOf(MystemDocument.class, benchmark.parseJson(data));
            MystemPreparedText preparedText =
                    assertInstanceOf(MystemPreparedText.class, benchmark.preprocessUnicode(data));
            List<?> tokens = assertInstanceOf(List.class, benchmark.tokenizeSearchTerms(data));
            int luceneTermLength = benchmark.luceneTokenStream(data);

            assertFalse(document.tokens().isEmpty());
            assertEquals(inputChars, document.originalText().length());
            int end = 0;
            for (var token : document.tokens()) {
                assertTrue(token.startOffset() >= end, token.toString());
                assertTrue(token.endOffset() > token.startOffset(), token.toString());
                assertTrue(token.endOffset() <= inputChars, token.toString());
                end = token.endOffset();
            }
            assertFalse(preparedText.issues().isEmpty());
            assertFalse(tokens.isEmpty());
            assertTrue(luceneTermLength > 0);
        } finally {
            data.tearDown();
        }
    }
}
