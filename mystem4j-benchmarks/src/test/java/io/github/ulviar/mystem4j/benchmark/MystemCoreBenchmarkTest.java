package io.github.ulviar.mystem4j.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemPreparedText;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemCoreBenchmarkTest {
    @Test
    void benchmarkSampleDataProducesNonEmptyResults() throws IOException {
        MystemCoreBenchmark benchmark = new MystemCoreBenchmark();
        MystemCoreBenchmark.SampleData data = new MystemCoreBenchmark.SampleData();
        data.setUp();
        try {
            MystemDocument document = assertInstanceOf(MystemDocument.class, benchmark.parseJson(data));
            MystemPreparedText preparedText =
                    assertInstanceOf(MystemPreparedText.class, benchmark.preprocessUnicode(data));
            List<?> tokens = assertInstanceOf(List.class, benchmark.tokenizeSearchTerms(data));
            int luceneTermLength = benchmark.luceneTokenStream(data);

            assertFalse(document.tokens().isEmpty());
            assertFalse(preparedText.issues().isEmpty());
            assertFalse(tokens.isEmpty());
            assertTrue(luceneTermLength > 0);
        } finally {
            data.tearDown();
        }
    }
}
